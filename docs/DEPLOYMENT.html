<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>ElectroShop — Beginner Deployment Guide</title>
<style>
  :root {
    --brand:#2563eb; --brand-dark:#1d4ed8; --ink:#0f172a; --muted:#64748b;
    --bg:#f8fafc; --card:#ffffff; --border:#e2e8f0; --ok:#16a34a; --okbg:#f0fdf4;
    --warn:#b45309; --warnbg:#fffbeb; --tipbg:#eff6ff;
  }
  * { box-sizing:border-box; }
  body { margin:0; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;
    background:var(--bg); color:var(--ink); line-height:1.65; }
  .wrap { max-width:920px; margin:0 auto; padding:32px 20px 80px; }
  header.hero { background:linear-gradient(135deg,var(--brand-dark),var(--brand)); color:#fff;
    border-radius:18px; padding:34px; margin-bottom:24px; }
  header.hero h1 { margin:0 0 8px; font-size:28px; }
  header.hero p { margin:0; opacity:.93; font-size:15px; }
  .toc { background:var(--card); border:1px solid var(--border); border-radius:14px; padding:18px 22px; margin-bottom:22px; }
  .toc b { font-size:13px; text-transform:uppercase; letter-spacing:.04em; color:var(--muted); }
  .toc ol { margin:10px 0 0; padding-left:22px; } .toc li { margin:4px 0; }
  .toc a { color:var(--ink); text-decoration:none; } .toc a:hover { color:var(--brand); }
  h2 { font-size:21px; margin:40px 0 6px; display:flex; align-items:center; gap:12px; scroll-margin-top:16px; }
  .num { display:inline-flex; align-items:center; justify-content:center; width:34px; height:34px;
    background:var(--brand); color:#fff; border-radius:50%; font-size:16px; font-weight:700; flex:none; }
  .lead { color:var(--muted); font-size:15px; margin:0 0 8px 46px; }
  .card { background:var(--card); border:1px solid var(--border); border-radius:14px; padding:20px 24px; margin:14px 0; }
  .what { background:var(--tipbg); border:1px solid #bfdbfe; border-radius:12px; padding:12px 16px; margin:6px 0 16px; font-size:14px; }
  .what b { color:var(--brand-dark); }
  .stack { display:grid; grid-template-columns:repeat(auto-fit,minmax(190px,1fr)); gap:12px; margin:8px 0 4px; }
  .pill { background:var(--card); border:1px solid var(--border); border-radius:12px; padding:14px; }
  .pill b { display:block; font-size:15px; } .pill span { color:var(--muted); font-size:13px; }
  code, .mono { font-family:'SF Mono',Menlo,Consolas,monospace; font-size:13px; }
  code { background:#eef2ff; color:#3730a3; padding:2px 6px; border-radius:5px; word-break:break-all; }
  pre { background:#0f172a; color:#e2e8f0; padding:14px 16px; border-radius:10px; overflow-x:auto; font-size:13px; }
  pre code { background:none; color:inherit; padding:0; }
  table { width:100%; border-collapse:collapse; margin:10px 0; font-size:14px; }
  th,td { text-align:left; padding:10px; border-bottom:1px solid var(--border); vertical-align:top; }
  th { color:var(--muted); font-weight:600; font-size:12px; text-transform:uppercase; letter-spacing:.03em; }
  td.k { white-space:nowrap; }
  .note { background:var(--warnbg); border:1px solid #fde68a; color:#7c5300; border-radius:10px; padding:12px 15px; margin:14px 0; font-size:14px; }
  .tip { background:var(--okbg); border:1px solid #bbf7d0; color:#166534; border-radius:10px; padding:12px 15px; margin:14px 0; font-size:14px; }
  .ok { color:var(--ok); font-weight:600; }
  ol.steps { margin:8px 0; padding-left:0; counter-reset:s; list-style:none; }
  ol.steps > li { position:relative; padding:4px 0 12px 34px; margin:0; }
  ol.steps > li::before { counter-increment:s; content:counter(s); position:absolute; left:0; top:2px;
    width:24px; height:24px; background:#e0e7ff; color:#3730a3; border-radius:50%; font-size:13px; font-weight:700;
    display:flex; align-items:center; justify-content:center; }
  ul.plain { margin:8px 0; } ul.plain li { margin:5px 0; }
  .kbd { background:#f1f5f9; border:1px solid var(--border); border-bottom-width:2px; border-radius:6px; padding:1px 7px; font-size:12px; white-space:nowrap; }
  a { color:var(--brand); }
  details { margin:8px 0; border:1px solid var(--border); border-radius:10px; padding:2px 14px; background:var(--card); }
  summary { cursor:pointer; font-weight:600; padding:10px 0; }
  details p, details ul { font-size:14px; }
  .foot { color:var(--muted); font-size:13px; margin-top:44px; text-align:center; }
  .chk { list-style:none; padding-left:0; } .chk li { padding:4px 0 4px 28px; position:relative; }
  .chk li::before { content:"☐"; position:absolute; left:0; font-size:16px; color:var(--brand); }
</style>
</head>
<body>
<div class="wrap">

<header class="hero">
  <h1>⚡ ElectroShop — Beginner Deployment Guide</h1>
  <p>A step-by-step guide to putting your shop online for free. No prior experience needed — every click is explained. Set aside about 45–60 minutes for your first time.</p>
</header>

<div class="toc">
  <b>What we'll do</b>
  <ol>
    <li><a href="#big">The big picture (read this first)</a></li>
    <li><a href="#accounts">Step 1 — Create your free accounts</a></li>
    <li><a href="#github">Step 2 — Upload the code to GitHub</a></li>
    <li><a href="#aiven">Step 3 — Create the database (Aiven)</a></li>
    <li><a href="#render">Step 4 — Put the backend online (Render)</a></li>
    <li><a href="#vercel">Step 5 — Put the website online (Vercel)</a></li>
    <li><a href="#connect">Step 6 — Connect them and open your shop</a></li>
    <li><a href="#admin">Step 7 — Log in as admin & set up your account</a></li>
    <li><a href="#trouble">Troubleshooting & things to know</a></li>
  </ol>
</div>

<!-- BIG PICTURE -->
<h2 id="big"><span class="num">0</span> The big picture</h2>
<p class="lead">Your app is made of three parts. Each one lives on a different free service. Here's what each part is, in plain words:</p>
<div class="card">
  <div class="stack">
    <div class="pill"><b>🖥️ The website (frontend)</b><span>The pages people see and click. Goes on <b>Vercel</b>.</span></div>
    <div class="pill"><b>⚙️ The engine (backend)</b><span>The "brain" that handles logins, orders, rules. Goes on <b>Render</b>.</span></div>
    <div class="pill"><b>🗄️ The database</b><span>Where users, the admin, and orders are stored. Goes on <b>Aiven</b>.</span></div>
  </div>
  <p style="margin:14px 0 0;font-size:14px">
    <b>How they talk to each other:</b> a visitor opens your website (Vercel). When they log in or place an order,
    the website asks the engine (Render). The engine saves and reads everything from the database (Aiven).
    You'll simply tell each service the address of the next one — that's most of the work.
  </p>
</div>
<div class="tip"><b>Tip:</b> Do the steps in order. Each step gives you an address or a password you'll paste into the next step. Keep a notes file open to paste them into as you go.</div>

<!-- STEP 1: ACCOUNTS -->
<h2 id="accounts"><span class="num">1</span> Create your free accounts</h2>
<p class="lead">You need four free accounts. The easiest path: create the GitHub one first, then use the "Sign in with GitHub" button on the other three.</p>
<div class="card">
  <ol class="steps">
    <li>Go to <a href="https://github.com" target="_blank">github.com</a> → click <span class="kbd">Sign up</span>. Enter your email, pick a username and password, confirm the email they send you.</li>
    <li>Go to <a href="https://aiven.io" target="_blank">aiven.io</a> → <span class="kbd">Get started for free</span> → choose <span class="kbd">Sign up with GitHub</span> → approve.</li>
    <li>Go to <a href="https://render.com" target="_blank">render.com</a> → <span class="kbd">Get Started</span> → <span class="kbd">GitHub</span> → approve.</li>
    <li>Go to <a href="https://vercel.com" target="_blank">vercel.com</a> → <span class="kbd">Sign Up</span> → <span class="kbd">Continue with GitHub</span> → approve.</li>
  </ol>
  <p style="margin:0;color:var(--muted);font-size:14px">All four are free and don't ask for a credit card for what we're doing.</p>
</div>

<!-- STEP 2: GITHUB -->
<h2 id="github"><span class="num">2</span> Upload the code to GitHub</h2>
<p class="lead">GitHub is like a cloud folder for code. Render and Vercel will read your code from here.</p>
<div class="what"><b>What you're doing:</b> creating an online folder ("repository") and putting the unzipped <code>electroshop</code> project inside it.</div>
<div class="card">
  <b>2a. Unzip the project on your computer</b>
  <ol class="steps">
    <li>Find <code>electroshop.zip</code> (the file I sent you) in your Downloads.</li>
    <li>Right-click it → <b>Extract All</b> (Windows) or double-click it (Mac). You now have a folder named <code>electroshop</code> containing <code>backend</code>, <code>frontend</code>, <code>database</code>, and more.</li>
  </ol>
</div>
<div class="card">
  <b>2b. Create the repository</b>
  <ol class="steps">
    <li>Go to <a href="https://github.com/new" target="_blank">github.com/new</a>.</li>
    <li><b>Repository name:</b> type <code>electroshop</code>.</li>
    <li>Leave it <b>Public</b> (or Private — both work). Do <b>not</b> tick "Add a README".</li>
    <li>Click <span class="kbd">Create repository</span>. You'll land on a mostly empty page — that's expected.</li>
  </ol>
</div>
<div class="card">
  <b>2c. Upload the files (no command line needed)</b>
  <ol class="steps">
    <li>On that new repository page, click the link <span class="kbd">uploading an existing file</span> (or <span class="kbd">Add file → Upload files</span>).</li>
    <li>Open the <code>electroshop</code> folder on your computer, select <b>everything inside it</b> (<span class="kbd">Ctrl/Cmd + A</span>), and drag it onto the GitHub upload area.</li>
    <li>Wait until all files finish uploading (you'll see a list appear).</li>
    <li>Scroll down, click the green <span class="kbd">Commit changes</span> button.</li>
  </ol>
  <div class="tip"><b>Done when:</b> refreshing the repository page shows the <code>backend</code> and <code>frontend</code> folders. That means your code is on GitHub. <span class="ok">✓</span></div>
  <details>
    <summary>Prefer the command line instead? (optional)</summary>
    <p>If you have Git installed, from inside the <code>electroshop</code> folder run:</p>
    <pre><code>git init
git add .
git commit -m "ElectroShop initial"
git branch -M main
git remote add origin https://github.com/&lt;your-username&gt;/electroshop.git
git push -u origin main</code></pre>
  </details>
</div>

<!-- STEP 3: AIVEN -->
<h2 id="aiven"><span class="num">3</span> Create the database (Aiven)</h2>
<p class="lead">This is where your users, the admin account, and all orders will be permanently stored.</p>
<div class="what"><b>What you're doing:</b> starting a free MySQL database and copying down its 5 connection details. You'll paste those into the backend in Step 4.</div>
<div class="card">
  <ol class="steps">
    <li>Log in to <a href="https://aiven.io" target="_blank">aiven.io</a>. If asked to create a "project", accept the default name and continue.</li>
    <li>Click <span class="kbd">Create service</span> (or <span class="kbd">+ Create service</span>).</li>
    <li>Choose <b>MySQL</b>.</li>
    <li>For the plan, pick the <b>Free</b> plan (it may be labeled "Free-1" — look for the one that says $0).</li>
    <li>Pick a <b>cloud region</b> close to you (any is fine; a European one is good for Romania).</li>
    <li>Service name: type <code>electroshop-db</code>. Click <span class="kbd">Create service</span>.</li>
    <li>Wait ~2–5 minutes. The status turns from "Rebuilding" to <span class="ok">Running</span>.</li>
  </ol>
</div>
<div class="card">
  <b>3b. Copy the connection details</b>
  <p>Click your <code>electroshop-db</code> service to open it. On the <b>Overview</b> tab, find <b>Connection information</b> and copy these 5 values into your notes:</p>
  <table>
    <tr><th>Label on Aiven</th><th>Example</th><th>You'll use it as…</th></tr>
    <tr><td class="k">Host</td><td class="mono">mysql-xxx.aivencloud.com</td><td>part of the database URL</td></tr>
    <tr><td class="k">Port</td><td class="mono">12345</td><td>part of the database URL</td></tr>
    <tr><td class="k">User</td><td class="mono">avnadmin</td><td>database username</td></tr>
    <tr><td class="k">Password</td><td class="mono">(long random text)</td><td>database password</td></tr>
    <tr><td class="k">Database name</td><td class="mono">defaultdb</td><td>part of the database URL</td></tr>
  </table>
  <p>Now build your <b>database URL</b> by filling Host, Port and Database name into this template (keep the <code>?ssl-mode=REQUIRED</code> at the end):</p>
  <pre><code>jdbc:mysql://HOST:PORT/defaultdb?ssl-mode=REQUIRED</code></pre>
  <p style="margin:0">Example result:</p>
  <pre><code>jdbc:mysql://mysql-xxx.aivencloud.com:12345/defaultdb?ssl-mode=REQUIRED</code></pre>
  <div class="tip">You don't need to create any tables. The backend creates them automatically the first time it starts, and it also creates the admin account for you.</div>
</div>

<!-- STEP 4: RENDER -->
<h2 id="render"><span class="num">4</span> Put the backend online (Render)</h2>
<p class="lead">The backend is the engine. Render will build it from your GitHub code and run it 24/7 (with a nap after inactivity).</p>
<div class="what"><b>What you're doing:</b> pointing Render at your GitHub repo, telling it to use the <code>backend</code> folder, and giving it the database details + a couple of settings.</div>
<div class="card">
  <ol class="steps">
    <li>Log in to <a href="https://render.com" target="_blank">render.com</a>.</li>
    <li>Click <span class="kbd">New +</span> (top right) → <span class="kbd">Web Service</span>.</li>
    <li>If asked, click <span class="kbd">Connect GitHub</span> and allow Render to see your repositories. Then find <code>electroshop</code> in the list and click <span class="kbd">Connect</span>.</li>
    <li>Now fill the form:
      <ul class="plain">
        <li><b>Name:</b> <code>electroshop-backend</code> (this becomes part of your web address).</li>
        <li><b>Region:</b> pick one near you (e.g. Frankfurt).</li>
        <li><b>Root Directory:</b> type <code>backend</code> &nbsp;← important!</li>
        <li><b>Runtime / Language:</b> choose <b>Docker</b> if shown. (If Render already detected it, leave it.)</li>
        <li><b>Instance Type / Plan:</b> select <b>Free</b>.</li>
      </ul>
    </li>
  </ol>
</div>
<div class="card">
  <b>4b. Add the environment variables</b>
  <p>Scroll to <b>Environment Variables</b> and click <span class="kbd">Add Environment Variable</span> for each row below. Type the <b>Key</b> exactly as shown, and the <b>Value</b> from your notes.</p>
  <table>
    <tr><th>Key (type exactly)</th><th>Value</th></tr>
    <tr><td class="k mono">SPRING_DATASOURCE_URL</td><td>your database URL from Step 3<br><span class="mono" style="color:var(--muted)">jdbc:mysql://HOST:PORT/defaultdb?ssl-mode=REQUIRED</span></td></tr>
    <tr><td class="k mono">SPRING_DATASOURCE_USERNAME</td><td>the <b>User</b> from Aiven (e.g. <code>avnadmin</code>)</td></tr>
    <tr><td class="k mono">SPRING_DATASOURCE_PASSWORD</td><td>the <b>Password</b> from Aiven</td></tr>
    <tr><td class="k mono">JWT_SECRET</td><td class="mono">o1GUewhFu9FvyMzTmRP1kANSP0WP30+9jtQcoS+guI2Ftbh4pR/Ii+QwdiedH14J</td></tr>
    <tr><td class="k mono">CORS_ORIGINS</td><td class="mono">https://placeholder.com</td></tr>
  </table>
  <p style="margin:0;font-size:14px;color:var(--muted)">Leave <code>CORS_ORIGINS</code> as the placeholder for now — you'll fix it in Step 6 once you know your website address.</p>
</div>
<div class="card">
  <b>4c. Create and wait</b>
  <ol class="steps">
    <li>Click <span class="kbd">Create Web Service</span> (or <span class="kbd">Deploy</span>) at the bottom.</li>
    <li>Render shows a black log screen and starts building. <b>First build takes 3–8 minutes</b> — this is normal. It's done when you see <span class="ok">"Live"</span> near the top and log lines like <code>Started ElectroShopApplication</code>.</li>
    <li>At the top you'll see your backend address, like <code>https://electroshop-backend.onrender.com</code>. <b>Copy it to your notes.</b></li>
    <li>Test it: open a new browser tab and visit that address + <code>/api/health</code>, e.g.<br><code>https://electroshop-backend.onrender.com/api/health</code><br>You should see <code>{"success":true,...,"status":"UP"}</code>. <span class="ok">✓ The engine works.</span></li>
  </ol>
  <div class="note"><b>If the build fails</b>, open the log, scroll to the first red <code>ERROR</code> line, and send it to me — usually it's one mistyped value. Most common cause: a wrong database URL or password.</div>
</div>

<!-- STEP 5: VERCEL -->
<h2 id="vercel"><span class="num">5</span> Put the website online (Vercel)</h2>
<p class="lead">This is the part your customers actually see and click.</p>
<div class="what"><b>What you're doing:</b> pointing Vercel at your GitHub repo, telling it to use the <code>frontend</code> folder, and telling it the backend address so the pages know where to send logins and orders.</div>
<div class="card">
  <ol class="steps">
    <li>Log in to <a href="https://vercel.com" target="_blank">vercel.com</a>.</li>
    <li>Click <span class="kbd">Add New…</span> → <span class="kbd">Project</span>.</li>
    <li>Find <code>electroshop</code> in the list and click <span class="kbd">Import</span>. (If you don't see it, click "Adjust GitHub App Permissions" and allow access.)</li>
    <li>On the configure screen:
      <ul class="plain">
        <li><b>Root Directory:</b> click <span class="kbd">Edit</span> → choose the <code>frontend</code> folder → <span class="kbd">Continue</span>. &nbsp;← important!</li>
        <li><b>Framework Preset:</b> it should auto-detect <b>Vite</b>. Leave it.</li>
      </ul>
    </li>
    <li>Open the <b>Environment Variables</b> section and add one:
      <table>
        <tr><th>Name</th><th>Value</th></tr>
        <tr><td class="k mono">VITE_API_URL</td><td>your backend address + <code>/api</code><br><span class="mono" style="color:var(--muted)">https://electroshop-backend.onrender.com/api</span></td></tr>
      </table>
    </li>
    <li>Click <span class="kbd">Deploy</span>. Wait 1–3 minutes.</li>
    <li>You'll see 🎉 and a <b>Visit</b> button with your website address, like <code>https://electroshop.vercel.app</code>. <b>Copy it to your notes.</b></li>
  </ol>
</div>

<!-- STEP 6: CONNECT -->
<h2 id="connect"><span class="num">6</span> Connect them and open your shop</h2>
<p class="lead">One last link: tell the backend to trust your website address (a security rule called CORS). Without this, logins would be blocked.</p>
<div class="card">
  <ol class="steps">
    <li>Go back to <a href="https://render.com" target="_blank">render.com</a> → open your <code>electroshop-backend</code> service.</li>
    <li>In the left menu click <span class="kbd">Environment</span>.</li>
    <li>Find <code>CORS_ORIGINS</code>, click edit (pencil), and replace the placeholder with your <b>Vercel address</b> — exactly, with <code>https://</code> and <b>no</b> slash at the end. Example: <code>https://electroshop.vercel.app</code></li>
    <li>Click <span class="kbd">Save Changes</span>. Render automatically restarts the backend (~1–2 min).</li>
    <li>Open your Vercel website address in the browser. <span class="ok">Your shop is live! 🎉</span></li>
  </ol>
</div>

<!-- STEP 7: ADMIN -->
<h2 id="admin"><span class="num">7</span> Log in as admin & set up your account</h2>
<p class="lead">The system created an administrator account for you automatically. Admins can manage users, products, and orders.</p>
<div class="card">
  <ol class="steps">
    <li>On your website, click <span class="kbd">Login</span> and sign in with:
      <table>
        <tr><th>Email</th><th>Password</th></tr>
        <tr><td class="mono">admin@electroshop.com</td><td class="mono">admin123</td></tr>
      </table>
    </li>
    <li>You'll now see an <span class="kbd">Admin</span> menu. Open it → you get the dashboard with charts, and tabs for <b>Products</b>, <b>Users</b>, and <b>Orders</b>.</li>
    <li><b>Make your own account an admin:</b> register a normal account for yourself (Register page), then as admin go to <b>Admin → Users</b>, click <b>Edit</b> on your account, tick <b>ADMIN</b>, and Save.</li>
    <li><b>Change the default admin password:</b> in <b>Admin → Users</b>, edit <code>admin@electroshop.com</code> and set a new password (or delete that account after your own is an admin).</li>
  </ol>
  <div class="tip"><b>Setting privileges</b> = giving/removing the ADMIN role on the Users page. USER = normal customer; ADMIN = full management access.</div>
</div>

<!-- TROUBLESHOOTING -->
<h2 id="trouble"><span class="num">?</span> Troubleshooting & things to know</h2>
<div class="card">
  <details open>
    <summary>The site is slow the first time I open it after a while</summary>
    <p>Normal on the free plan. The backend "sleeps" after 15 minutes of no traffic; the first request wakes it up and takes ~30–60 seconds. After that it's fast until it sleeps again.</p>
  </details>
  <details>
    <summary>I logged in but nothing happens / "Network error"</summary>
    <p>Almost always the <code>CORS_ORIGINS</code> value on Render doesn't exactly match your Vercel address, or <code>VITE_API_URL</code> on Vercel is wrong. Check both: no trailing slash on CORS_ORIGINS, and <code>VITE_API_URL</code> must end with <code>/api</code>. After changing a Vercel variable, click <b>Redeploy</b> on Vercel.</p>
  </details>
  <details>
    <summary>The backend build failed on Render</summary>
    <p>Open the log, find the first red <code>ERROR</code> line. The usual cause is a wrong database URL/username/password. Fix the value under Render → Environment and click <b>Manual Deploy → Deploy latest commit</b>. Send me the error line if unsure.</p>
  </details>
  <details>
    <summary>Product image upload doesn't stick</summary>
    <p>On the free Render plan, uploaded image files are temporary (erased on each redeploy). For now, when adding a product use the <b>Image URL</b> field with a link to an image hosted elsewhere. This does not affect users, orders, or accounting.</p>
  </details>
  <details>
    <summary>Will I lose my data?</summary>
    <p>No. All users and orders live in the Aiven database, which is persistent. Redeploying the backend or frontend never touches your data. Aiven may pause the database after long inactivity, but your data stays and it wakes on the next connection.</p>
  </details>
  <details>
    <summary>Is this secure enough?</summary>
    <p>For getting started, yes: passwords are encrypted (BCrypt), logins use secure tokens (JWT), and admin-only areas are protected. Keep your <code>JWT_SECRET</code> private, and change the default admin password. When you have real customers, we can add HTTPS custom domains, backups, and a paid database tier.</p>
  </details>
</div>

<div class="card">
  <b>✅ Quick checklist</b>
  <ul class="chk">
    <li>4 accounts created (GitHub, Aiven, Render, Vercel)</li>
    <li>Code uploaded to GitHub (backend + frontend folders visible)</li>
    <li>Aiven MySQL running; 5 connection details saved</li>
    <li>Render backend "Live"; <code>/api/health</code> returns UP</li>
    <li>Vercel website deployed; address saved</li>
    <li>CORS_ORIGINS on Render set to the Vercel address</li>
    <li>Logged in as admin; own account promoted; admin password changed</li>
  </ul>
</div>

<h2>➡️ Next step (once it's live)</h2>
<div class="card">
  <p>The <b>primary accounting</b> module: I'll add suppliers + stock intake (purchases) and a report showing revenue (sales), expenses (purchases), and profit/margin — on top of this same database, without losing any data.</p>
</div>

<p class="foot">ElectroShop · Beginner deployment guide · keep this file as a reference and follow it top to bottom</p>

</div>
</body>
</html>
