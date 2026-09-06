#!/usr/bin/env node
/**
 * Optimises all GLB animations in R2 (dedup + prune — no Draco).
 *
 * Draco is intentionally omitted: the app renders GLBs with Three.js via
 * expo-gl. DRACOLoader in React Native requires Web Workers which are not
 * available in Hermes. dedup + prune still yields ~10–25 % size reduction
 * and HTTP caching (public R2 URLs with Cache-Control: immutable) covers
 * the first-load cost after that.
 *
 * Usage:
 *   cd scripts && npm install
 *   node compress-animations.mjs            # optimise + re-upload
 *   node compress-animations.mjs --dry-run  # show savings, no upload
 *
 * Required env vars: R2_ENDPOINT, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY
 * Optional env vars: R2_BUCKET_NAME (default: signa-animations)
 *
 * Re-uploaded objects get:
 *   Cache-Control: public, max-age=31536000, immutable
 * so Cloudflare CDN and WebView HTTP caches keep them indefinitely.
 */

import {
  CopyObjectCommand,
  GetObjectCommand,
  ListObjectsV2Command,
  PutObjectCommand,
  S3Client,
} from "@aws-sdk/client-s3";
import { NodeIO } from "@gltf-transform/core";
import { KHRONOS_EXTENSIONS } from "@gltf-transform/extensions";
import { dedup, prune } from "@gltf-transform/functions";

// ── Config ────────────────────────────────────────────────────────────────────

const BUCKET = process.env.R2_BUCKET_NAME ?? "signa-animations";
const DRY_RUN = process.argv.includes("--dry-run");
const CACHE_CONTROL = "public, max-age=31536000, immutable";

for (const v of ["R2_ENDPOINT", "R2_ACCESS_KEY_ID", "R2_SECRET_ACCESS_KEY"]) {
  if (!process.env[v]) {
    console.error(`Missing required env var: ${v}`);
    process.exit(1);
  }
}

const s3 = new S3Client({
  endpoint: process.env.R2_ENDPOINT,
  region: "auto",
  credentials: {
    accessKeyId: process.env.R2_ACCESS_KEY_ID,
    secretAccessKey: process.env.R2_SECRET_ACCESS_KEY,
  },
  forcePathStyle: true,
});

// ── gltf-transform setup ──────────────────────────────────────────────────────

const io = new NodeIO().registerExtensions(KHRONOS_EXTENSIONS);

// ── Helpers ───────────────────────────────────────────────────────────────────

function fmt(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1_048_576) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1_048_576).toFixed(1)} MB`;
}

// ── List GLBs ─────────────────────────────────────────────────────────────────

console.log(`Listing GLBs in '${BUCKET}'…`);
const keys = [];
let token;
do {
  const res = await s3.send(
    new ListObjectsV2Command({ Bucket: BUCKET, ContinuationToken: token })
  );
  for (const obj of res.Contents ?? []) {
    if (obj.Key.endsWith(".glb")) keys.push(obj.Key);
  }
  token = res.NextContinuationToken;
} while (token);

console.log(
  `Found ${keys.length} GLB file(s)${DRY_RUN ? " — dry run, no uploads" : ""}\n`
);

// ── Process ───────────────────────────────────────────────────────────────────

let totalOriginal = 0;
let totalCompressed = 0;
let processed = 0;
let failed = 0;

for (const key of keys) {
  process.stdout.write(`  ${key} … `);
  try {
    // Download
    const { Body, ContentLength } = await s3.send(
      new GetObjectCommand({ Bucket: BUCKET, Key: key })
    );
    const originalBytes = await Body.transformToByteArray();
    const originalSize = originalBytes.byteLength;

    // Parse and optimise
    const document = await io.readBinary(originalBytes);

    // dedup: merges identical accessors / textures (free win)
    // prune: removes orphaned nodes, materials, textures
    await document.transform(dedup(), prune());

    const compressedBytes = await io.writeBinary(document);
    const compressedSize = compressedBytes.byteLength;
    const pct = ((1 - compressedSize / originalSize) * 100).toFixed(1);

    if (!DRY_RUN) {
      await s3.send(
        new PutObjectCommand({
          Bucket: BUCKET,
          Key: key,
          Body: Buffer.from(compressedBytes),
          ContentType: "model/gltf-binary",
          CacheControl: CACHE_CONTROL,
        })
      );
    }

    totalOriginal += originalSize;
    totalCompressed += compressedSize;
    processed++;
    console.log(`${fmt(originalSize)} → ${fmt(compressedSize)} (−${pct}%)`);
  } catch (err) {
    console.log(`ERROR: ${err.message}`);
    failed++;
  }
}

// ── Summary ───────────────────────────────────────────────────────────────────

console.log(`\n${"─".repeat(52)}`);
console.log(`Processed: ${processed}   Failed: ${failed}`);
if (processed > 0) {
  const totalPct = ((1 - totalCompressed / totalOriginal) * 100).toFixed(1);
  console.log(
    `Total    : ${fmt(totalOriginal)} → ${fmt(totalCompressed)} (−${totalPct}%)`
  );
}
if (DRY_RUN) console.log("\nDry run — no files were modified.");
