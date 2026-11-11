# Default Android and library consumer rules are enough for milestone 1.
# Room, SQLCipher, Health Connect and ML Kit add their keep rules as they arrive.

# SQLCipher calls back into these from native code.
-keep class net.zetetic.database.** { *; }
-keepclassmembers class net.zetetic.database.** { *; }
