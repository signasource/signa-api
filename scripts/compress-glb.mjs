/**
 * Compresses all GLB files in a directory.
 *
 * Per file:
 *   - Textures: resize to MAX_SIZE px (default 512), re-encode as JPEG q=80
 *     (RGBA textures keep PNG to preserve alpha)
 *   - Mesh: Draco compression
 *   - Cleanup: dedup, prune, resample animation keyframes
 *
 * Usage:
 *   node scripts/compress-glb.mjs <input-dir> [output-dir]
 *
 * Setup (run once):
 *   npm install
 */

import { NodeIO } from "@gltf-transform/core";
import { KHRONOS_EXTENSIONS } from "@gltf-transform/extensions";
import { dedup, draco, resample, prune } from "@gltf-transform/functions";
import draco3d from "draco3d";
import { Jimp } from "jimp";
import { readdirSync, statSync, mkdirSync } from "fs";
import { join, extname } from "path";

const MAX_SIZE = 512;

const inputDir = process.argv[2];
const outputDir = process.argv[3] ?? inputDir;

if (!inputDir) {
  console.error("Usage: node scripts/compress-glb.mjs <input-dir> [output-dir]");
  process.exit(1);
}

const io = new NodeIO()
  .registerExtensions(KHRONOS_EXTENSIONS)
  .registerDependencies({
    "draco3d.decoder": await draco3d.createDecoderModule(),
    "draco3d.encoder": await draco3d.createEncoderModule(),
  });

const files = readdirSync(inputDir).filter((f) => extname(f).toLowerCase() === ".glb");
if (files.length === 0) {
  console.log("No .glb files found in", inputDir);
  process.exit(0);
}

mkdirSync(outputDir, { recursive: true });

let totalBefore = 0;
let totalAfter = 0;
let errorCount = 0;

for (const file of files) {
  const inputPath = join(inputDir, file);
  const outputPath = join(outputDir, file);
  const sizeBefore = statSync(inputPath).size;

  process.stdout.write(`  ${file} (${kb(sizeBefore)} KB) → `);

  try {
    const doc = await io.read(inputPath);

    // Compress textures with jimp (pure JS, no libvips).
    const textures = doc.getRoot().listTextures();
    for (const texture of textures) {
      const raw = texture.getImage();
      if (!raw) continue;

      try {
        const img = await Jimp.read(Buffer.from(raw));
        const hasAlpha = img.hasAlpha();

        // Resize preserving aspect ratio so neither dimension exceeds MAX_SIZE.
        if (img.width > MAX_SIZE || img.height > MAX_SIZE) {
          img.scaleToFit({ w: MAX_SIZE, h: MAX_SIZE });
        }

        let compressed;
        if (hasAlpha) {
          // Keep PNG to preserve transparency.
          compressed = await img.getBuffer("image/png");
          texture.setMimeType("image/png");
        } else {
          compressed = await img.getBuffer("image/jpeg", { quality: 80 });
          texture.setMimeType("image/jpeg");
        }
        texture.setImage(compressed);
      } catch (texErr) {
        // Leave texture untouched if jimp can't decode it.
        process.stderr.write(`\n    warn: texture skipped — ${texErr.message}`);
      }
    }

    await doc.transform(
      dedup(),
      prune(),
      resample(),
      draco(),
    );

    await io.write(outputPath, doc);

    const sizeAfter = statSync(outputPath).size;
    const pct = Math.round((1 - sizeAfter / sizeBefore) * 100);
    console.log(`${kb(sizeAfter)} KB  (-${pct}%)`);

    totalBefore += sizeBefore;
    totalAfter += sizeAfter;
  } catch (err) {
    console.log(`ERROR — ${err.message}`);
    errorCount++;
  }
}

if (totalBefore > 0) {
  const totalPct = Math.round((1 - totalAfter / totalBefore) * 100);
  console.log(`\nTotal: ${kb(totalBefore)} KB → ${kb(totalAfter)} KB  (-${totalPct}%)`);
}
if (errorCount > 0) {
  console.log(`${errorCount} file(s) failed.`);
}

function kb(bytes) {
  return (bytes / 1024).toFixed(0);
}
