# Screen Time Guard remote parent unlock

This folder contains a small Cloudflare Worker + D1 relay used by the parent remote unlock feature.

## Security model

- The protected phone generates a random device ID and a 256-bit pairing secret.
- The relay stores only `SHA-256(secret)`.
- The parent pairing link keeps the secret after `#` in the URL fragment, so the browser does not send it while loading the page.
- Every API call that can read or create a remote command must authenticate with `Authorization: Bearer <secret>`.
- Remote commands expire after 2 minutes and are acknowledged after the protected phone consumes them.
- Remote screen-time control can grant a 15, 30, or 60 minute override, or an override until local midnight.
- Remote app-install control can temporarily allow app installation for exactly 15 minutes. The normal installation block is automatically reapplied afterwards.
- Allowing app installation does not itself unlock screen-time restricted mode; these remain separate guardian controls.
- Remote control cannot permanently disable Device Owner, uninstall protection, installation protection, AdGuard protection, or change the guardian PIN.
- If the network or relay is unavailable, the protected phone remains protected (fail-closed).

## Deploy the relay

Requirements: a Cloudflare account and Node.js/npm with Wrangler available.

From this `remote/` directory:

1. Log in to Cloudflare:

   ```bash
   npx wrangler@latest login
   ```

2. Create a D1 database:

   ```bash
   npx wrangler@latest d1 create screentimeguard-remote
   ```

3. Copy `wrangler.toml.example` to `wrangler.toml` and replace `REPLACE_WITH_D1_DATABASE_ID` with the database ID returned by the previous command.

4. Create the tables in the remote database:

   ```bash
   npx wrangler@latest d1 execute screentimeguard-remote --remote --file=./schema.sql
   ```

5. Deploy the Worker:

   ```bash
   npx wrangler@latest deploy
   ```

Wrangler will print an HTTPS `workers.dev` URL. Enter that URL in **Parent remote unlock** on the protected phone.

## Pair the guardian phone

1. On the protected phone, open the persistent Screen Time Guard notification and tap **Parent remote setup**.
2. Enter the guardian PIN.
3. Paste the deployed HTTPS Worker URL and tap **Save & enable remote unlock**.
4. Tap **Share guardian pairing link** and send the link directly to the guardian.
5. Open the link once on the guardian phone. The page saves the device ID and pairing secret in that browser's local storage and removes the secret from the visible URL.

The guardian can then choose **Unlock 15 min**, **Unlock 30 min**, **Unlock 1 hour**, **Until midnight**, or **Allow app installs for 15 min**.

## Revoke access

On the protected phone, open **Parent remote unlock** with the guardian PIN and choose **Disable & revoke remote pairing**. The app stops polling immediately, asks the relay to delete the registration, and rotates the local device ID + pairing secret even if the relay is offline.
