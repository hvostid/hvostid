import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import http from 'node:http';

// Dev shim for the X-Accel-Redirect handoff that the prod nginx (see
// frontend/nginx.conf) performs natively. In production the chain is
// browser -> nginx -> gateway -> passport-service; passport-service replies
// 204 + `X-Accel-Redirect: /_protected/minio/<bucket>/<obj>?<sig>` and the
// edge nginx swaps in the MinIO body via an internal subrequest, signing
// the request with Host: minio:9000 because that's what the SDK signed.
//
// `npm run dev` skips that nginx and forwards /api/* through Vite's HTTP
// proxy, which leaves the empty 204 in place and breaks every <img> that
// hits the media endpoints. This middleware mirrors the nginx behavior:
// on any X-Accel-Redirect from upstream, we fetch the MinIO object from
// the host-published port with the signed Host header and stream the
// response back to the browser.
const ACCEL_PREFIX = '/_protected/minio';
const DEV_MINIO_HOST = process.env.VITE_DEV_MINIO_HOST ?? 'localhost';
const DEV_MINIO_PORT = Number(process.env.VITE_DEV_MINIO_PORT ?? '9000');
// Must match the endpoint the passport-service SDK signs against -- typically
// the docker-compose service name + port. If you change MINIO_HOST in
// docker-compose.yml, update this (or set VITE_SIGNED_MINIO_HOST).
const SIGNED_MINIO_HOST = process.env.VITE_SIGNED_MINIO_HOST ?? 'minio:9000';

function pipeUpstream(proxyRes, res) {
    for (const [key, value] of Object.entries(proxyRes.headers)) {
        if (value !== undefined) res.setHeader(key, value);
    }
    res.writeHead(proxyRes.statusCode ?? 502);
    proxyRes.pipe(res);
}

function handleXAccel(proxyRes, res, target) {
    // Discard the empty body of the original 204.
    proxyRes.resume();

    if (!target.startsWith(ACCEL_PREFIX)) {
        res.writeHead(502, { 'Content-Type': 'text/plain' });
        res.end(`Unexpected X-Accel-Redirect target: ${target}`);
        return;
    }

    const minioPath = target.slice(ACCEL_PREFIX.length);
    const sub = http.request(
        {
            host: DEV_MINIO_HOST,
            port: DEV_MINIO_PORT,
            method: 'GET',
            path: minioPath,
            headers: { Host: SIGNED_MINIO_HOST },
        },
        (subRes) => {
            for (const [key, value] of Object.entries(subRes.headers)) {
                // Skip MinIO bookkeeping nginx hides in prod.
                if (key.toLowerCase().startsWith('x-amz-')) continue;
                if (value !== undefined) res.setHeader(key, value);
            }
            res.setHeader('Cache-Control', 'private, max-age=300, immutable');
            res.setHeader('Referrer-Policy', 'no-referrer');
            res.writeHead(subRes.statusCode ?? 502);
            subRes.pipe(res);
        }
    );
    sub.on('error', (err) => {
        if (!res.headersSent) {
            res.writeHead(502, { 'Content-Type': 'text/plain' });
        }
        res.end(`MinIO subrequest failed: ${err.message}`);
    });
    sub.end();
}

export default defineConfig({
    plugins: [react(), tailwindcss()],
    server: {
        port: 3000,
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                selfHandleResponse: true,
                configure: (proxy) => {
                    proxy.on('proxyRes', (proxyRes, _req, res) => {
                        const target = proxyRes.headers['x-accel-redirect'];
                        if (typeof target === 'string' && target.length > 0) {
                            handleXAccel(proxyRes, res, target);
                            return;
                        }
                        pipeUpstream(proxyRes, res);
                    });
                },
            },
        },
    },
});
