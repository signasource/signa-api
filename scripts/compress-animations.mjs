#!/usr/bin/env node
/**
 * Batch-compresses all GLB animations in R2 using Draco + free mesh opts.
 *
 * Usage:
 *   cd scripts && npm install
 *   node compress-animations.mjs            # compress + re-upload
 *   node compress-animations.mjs --dry-run  # show savings, no upload
 *
 * Required env vars:
 *   R2_ENDPOINT          e.g. https://<account>.r2.cloudflarestorage.com
 *   R2_ACCESS_KEY_ID
 *   R2_SECRET_ACCESS_KEY
 * Optional:
 *   R2_BUCKET_NAME       default: signa-animations
 *
 * Draco settings: encodeSpeed=0 (best ratio) / decodeSpeed=10 (fastest on device).
 * Already-compressed files (KHR_draco_mesh_compression) are skipped automatically.
 * model-viewer includes the Draco decoder, so no frontend changes are needed.
 */

import {
  GetObjectCommand,
  ListObjectsV2Command,
  PutObjectCommand,
  S3Client,
} from "@aws-sdk/client-s3";
import { NodeIO } from "@gltf-transform/core";
import { KHRONOS_EXTENSIONS } from "@gltf-transform/extensions";
import { dedup, draco, prune } from "@gltf-transform/functions";
import draco3d from "draco3dgltf";

// ── Config ────────────────────────────────────────────────────────────────────

const BUCKET = process.env.R2_BUCKET_NAME ?? "signa-animations";
const DRY_RUN = process.argv.includes("--dry-run");

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

console.log("Loading Draco encoder/decoder…");
const [encoder, decoder] = await Promise.all([
  draco3d.createEncoderModule(),
  draco3d.createDecoderModule(),
]);

const io = new NodeIO()
  .registerExtensions(KHRONOS_EXTENSIONS)
  .registerDependencies({ "draco3d.encoder": encoder, "draco3d.decoder": decoder });

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
let skipped = 0;
let failed = 0;

for (const key of keys) {
  process.stdout.write(`  ${key} … `);
  try {
    // Download
    const { Body } = await s3.send(
      new GetObjectCommand({ Bucket: BUCKET, Key: key })
    );
    const originalBytes = await Body.transformToByteArray();
    const originalSize = originalBytes.byteLength;

    // Parse
    const document = await io.readBinary(originalBytes);

    // Skip already-compressed files
    const alreadyDraco = document
      .getRoot()
      .listExtensionsUsed()
      .some((e) => e.extensionName === "KHR_draco_mesh_compression");
    if (alreadyDraco) {
      console.log("already Draco-compressed, skipping");
      skipped++;
      continue;
    }

    // dedup:  merges identical accessors/textures (free size win)
    // prune:  removes unused nodes, materials, textures
    // draco:  Draco geometry compression — encodeSpeed=0 maximises ratio,
    //         decodeSpeed=10 keeps decompression fast on the device
    await document.transform(
      dedup(),
      prune(),
      draco({ encodeSpeed: 0, decodeSpeed: 10 })
    );

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
console.log(
  `Processed: ${processed}   Skipped: ${skipped}   Failed: ${failed}`
);
if (processed > 0) {
  const totalPct = ((1 - totalCompressed / totalOriginal) * 100).toFixed(1);
  console.log(
    `Total    : ${fmt(totalOriginal)} → ${fmt(totalCompressed)} (−${totalPct}%)`
  );
}
if (DRY_RUN) console.log("\nDry run — no files were modified.");
