package com.electroshop.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Thin wrapper over the Cloudinary SDK for hosting product images.
 *
 * <p>Configured entirely from environment variables so no secret ever lands in
 * the repo or the frontend:</p>
 * <pre>
 *   CLOUDINARY_CLOUD_NAME
 *   CLOUDINARY_API_KEY
 *   CLOUDINARY_API_SECRET
 * </pre>
 * If those are absent the service is inert ({@link #isConfigured()} is false) and
 * upload attempts fail with a clear message instead of a cryptic NPE.
 */
@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final boolean configured;

    public CloudinaryService(
            @Value("${CLOUDINARY_CLOUD_NAME:}") String cloudName,
            @Value("${CLOUDINARY_API_KEY:}") String apiKey,
            @Value("${CLOUDINARY_API_SECRET:}") String apiSecret) {
        this.configured = notBlank(cloudName) && notBlank(apiKey) && notBlank(apiSecret);
        this.cloudinary = configured
                ? new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", cloudName.trim(),
                        "api_key", apiKey.trim(),
                        "api_secret", apiSecret.trim(),
                        "secure", true))
                : null;
    }

    public boolean isConfigured() {
        return configured;
    }

    /** Uploads the file bytes to the given folder and returns the delivered URL + public id. */
    public UploadResult upload(MultipartFile file, String folder) {
        ensureConfigured();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image"));
            return new UploadResult(
                    (String) result.get("secure_url"),
                    (String) result.get("public_id"));
        } catch (IOException e) {
            throw new IllegalStateException("Încărcarea imaginii pe Cloudinary a eșuat: " + e.getMessage(), e);
        }
    }

    /** Best-effort delete — a Cloudinary hiccup must not block DB cleanup. */
    public void delete(String publicId) {
        if (!configured || publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image"));
        } catch (Exception ignored) {
            // swallow — the DB row is being removed regardless
        }
    }

    private void ensureConfigured() {
        if (!configured) {
            throw new IllegalStateException(
                    "Cloudinary nu este configurat (lipsesc variabilele de mediu CLOUDINARY_*).");
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public record UploadResult(String url, String publicId) {}
}
