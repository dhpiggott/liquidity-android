# Various superfluous inferred dependencies that don't exist on Android
-dontwarn java.beans.MethodDescriptor
-dontwarn java.beans.PropertyDescriptor
-dontwarn java.beans.SimpleBeanInfo
-dontwarn java.lang.management.*
-dontwarn java.nio.file.*
-dontwarn javax.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry
-dontwarn scala.reflect.internal.*
-dontwarn sun.**

# Scala Fork/Join
-keep class scala.concurrent.forkjoin.** { *; }

# See https://issues.scala-lang.org/browse/SI-5397
-keep class scala.collection.SeqLike {
    public protected *;
}

# JSON
-keep class com.fasterxml.jackson.** { *; }

# Preference compat types are referenced in XML
-keep class android.support.v7.preference.** { *; }

# Design support behaviours are referenced in XML
-keep class android.support.design.widget.** { *; }

# MaterialProgressBar
-keep class me.zhanghai.android.materialprogressbar.** { *; }
