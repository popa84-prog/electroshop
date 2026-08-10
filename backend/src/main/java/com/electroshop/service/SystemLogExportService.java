package com.electroshop.service;

import com.electroshop.dto.ActivityFeedDto;
import com.electroshop.dto.SystemLogsDto;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CSV export for the operational log and the activity feed.
 *
 * <p>Tasks 5 and 19.</p>
 *
 * <h2>Formula injection is neutralised</h2>
 *
 * <p>These files are opened in Excel. A cell whose text begins with {@code =},
 * {@code +}, {@code -}, {@code @}, a tab or a carriage return is interpreted as a
 * formula, and a formula can call external functions — which turns "export the audit log"
 * into "run whatever an attacker wrote into a product name three weeks ago". Every field
 * that could begin with one of those characters is prefixed with an apostrophe, which
 * Excel treats as "this is text" and which is invisible in the cell.</p>
 *
 * <p>This matters more here than almost anywhere else in the application: the audit log
 * and the operational log exist to record what untrusted parties did, so their contents
 * are untrusted by construction.</p>
 *
 * <h2>UTF-8 with a byte-order mark</h2>
 *
 * <p>Excel on Windows assumes the system code page unless a BOM says otherwise, which
 * turns every Romanian diacritic into a pair of wrong characters. Three bytes at the
 * front of the file fix it, and their absence is the single most common complaint about
 * a CSV export that is otherwise correct.</p>
 */
@Service
public class SystemLogExportService {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** UTF-8 byte-order mark, so Excel reads the file in the right encoding. */
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Characters that make Excel treat a cell as a formula. */
    private static final String FORMULA_STARTERS = "=+-@\t\r";

    /** The operational log as a CSV file. */
    public byte[] exportLogs(List<SystemLogsDto.LogEntry> entries) {
        StringBuilder sb = new StringBuilder(entries.size() * 160 + 128);
        sb.append("Data,Sursa,Nivel,Cod,Mesaj,Context,Status HTTP,Durata (ms),Detalii\n");

        for (SystemLogsDto.LogEntry e : entries) {
            appendRow(sb,
                    e.createdAt() == null ? "" : e.createdAt().format(TIMESTAMP),
                    e.source(),
                    e.level(),
                    e.code(),
                    e.message(),
                    e.context(),
                    e.statusCode() == null ? "" : String.valueOf(e.statusCode()),
                    e.durationMs() == null ? "" : String.valueOf(e.durationMs()),
                    e.detail());
        }
        return withBom(sb.toString());
    }

    /** The activity feed as a CSV file. */
    public byte[] exportActivity(List<ActivityFeedDto.Entry> entries) {
        StringBuilder sb = new StringBuilder(entries.size() * 160 + 128);
        sb.append("Data,Utilizator,Actiune,Categorie,Tip entitate,ID entitate,Detalii,Modificari\n");

        for (ActivityFeedDto.Entry e : entries) {
            sb.append("");
            appendRow(sb,
                    e.createdAt() == null ? "" : e.createdAt().format(TIMESTAMP),
                    e.actor(),
                    e.actionLabel(),
                    e.category(),
                    e.entityType(),
                    e.entityId() == null ? "" : String.valueOf(e.entityId()),
                    e.details(),
                    renderChanges(e.changes()));
        }
        return withBom(sb.toString());
    }

    /**
     * Renders field changes into one cell.
     *
     * <p>An empty result when nothing was parsed, rather than a placeholder. The column
     * being blank says "no structured diff was recorded", which is true; a dash would
     * read as "nothing changed", which is not.</p>
     */
    private static String renderChanges(List<ActivityFeedDto.FieldChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ActivityFeedDto.FieldChange change : changes) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(change.field())
                    .append(": ")
                    .append(change.oldValue() == null ? "—" : change.oldValue())
                    .append(" → ")
                    .append(change.newValue() == null ? "—" : change.newValue());
        }
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        sb.append('\n');
    }

    /**
     * Quotes a value for CSV and defuses anything Excel would treat as a formula.
     *
     * <p>Order matters: the formula guard goes on first, then the whole thing is quoted.
     * Prefixing after quoting would put the apostrophe outside the quotes, where it is a
     * visible stray character rather than a text marker.</p>
     */
    static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String cleaned = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');

        if (!cleaned.isEmpty() && FORMULA_STARTERS.indexOf(cleaned.charAt(0)) >= 0) {
            cleaned = "'" + cleaned;
        }

        return '"' + cleaned.replace("\"", "\"\"") + '"';
    }

    private static byte[] withBom(String body) {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[BOM.length + content.length];
        System.arraycopy(BOM, 0, out, 0, BOM.length);
        System.arraycopy(content, 0, out, BOM.length, content.length);
        return out;
    }
}
