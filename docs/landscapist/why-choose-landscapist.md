# Why Choose Landscapist?

## 1. Minimal Footprint for SDKs and Libraries

Landscapist Core is **exceptionally lightweight** compared to other image loading libraries, making it the ideal choice for SDK and library developers who need to minimize their dependency footprint. When building SDKs or libraries that require image loading capabilities, the size of your dependencies directly impacts your users' APK size.

**AAR Size Comparison (Android Release Build):**

| Library | AAR Size | vs Landscapist Core | Impact on APK |
|---------|----------|---------------------|---------------|
| **landscapist-core** | **~145 KB** | **Baseline (Smallest)** | Minimal |
| Glide | ~527 KB | **+263% larger** | Significant |
| Coil3 | ~312 KB | **+115% larger** | Moderate |
| Fresco | ~3.2 MB | **+2,107% larger** | Very High |

**Why this matters for SDKs:**
- **User Impact**: Every KB in your SDK adds to your users' APK size. Landscapist Core keeps your SDK lean.
- **Adoption Rate**: Developers are more likely to adopt lightweight SDKs that don't bloat their apps.
- **Multiple SDK Scenario**: When apps use multiple SDKs, each using lightweight dependencies prevents exponential growth.
- **Enterprise Requirements**: Many enterprises have strict APK size budgets. Landscapist Core helps you stay within limits.

**Real-world example**: If your SDK uses Landscapist Core instead of Glide, you save **~382 KB per user**. For 1 million users, that's **382 GB of bandwidth** and storage savings across all devices.

## 2. Cross-Platform from the Start

Write your image loading code once and deploy it everywhere. `LandscapistImage` works identically across all Compose Multiplatform targets without platform-specific workarounds or conditional code. Share your image loading logic, caching configuration, and UI components across your entire application.

## 3. Full Control Over the Pipeline

Unlike wrapper libraries that hide implementation details, `LandscapistImage` exposes the entire image loading pipeline. Configure network timeouts, cache policies, image transformations, and loading priorities at both the global and per-request levels. You're not locked into preset behaviors—customize everything to match your app's specific needs.

## 4. Performance Optimized

Built with performance in mind from day one. `LandscapistImage` automatically downsamples images based on display size, uses efficient memory and disk caching strategies, and supports progressive loading for large images. Memory usage is optimized through weak reference pooling and automatic cache trimming under memory pressure.

See the [Performance Comparison](performance-comparison.md) for detailed benchmark results.

## 5. Plugin Ecosystem

Leverage the full Landscapist plugin ecosystem including shimmer placeholders, crossfade animations, blur transformations, palette extraction, and zoomable images. All plugins work seamlessly with `LandscapistImage` and can be combined in any way you need.

## See Also

- [Performance Comparison](performance-comparison.md) - Detailed performance benchmarks
- [Landscapist Core](landscapist-core.md) - Core image loading engine
- [Landscapist Image](landscapist-image.md) - Compose UI component
