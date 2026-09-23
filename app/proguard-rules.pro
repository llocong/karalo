# R8 rules for release builds (see app/build.gradle.kts). Libraries that ship their own consumer
# rules (Compose, Hilt, Media3, Firebase, kotlinx.serialization, OkHttp) need nothing here, nor do
# @JavascriptInterface methods (PoTokenWebView), which the default optimize rules already keep.

# NewPipeExtractor (via :youtube-client), same rules the NewPipe app itself ships with: it loads its
# "time ago" patterns reflectively by locale, and runs YouTube's player JavaScript through Rhino,
# which generates and loads classes at runtime.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter

# Rhino references desktop-JVM-only APIs (javax.script, java.beans, jdk.dynalink) from code paths
# NewPipeExtractor never uses on Android.
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
