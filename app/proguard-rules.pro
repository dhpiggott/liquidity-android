-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-dontwarn javax.naming.**
-dontwarn com.sothree.**
-dontwarn com.nimbusds.jose.**
-dontwarn org.bouncycastle.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.OpenSSLProvider
-dontwarn sun.misc.Unsafe

-keep class android.support.v7.app.AppCompatViewInflater { *; }
