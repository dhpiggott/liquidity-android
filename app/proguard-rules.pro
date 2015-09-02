-dontwarn java.beans.MethodDescriptor
-dontwarn java.beans.PropertyDescriptor
-dontwarn java.beans.SimpleBeanInfo
-dontwarn java.lang.management.*
-dontwarn java.nio.file.*
-dontwarn java.rmi.*
-dontwarn javax.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry
-dontwarn scala.reflect.internal.*
-dontwarn sun.**
-keep class scala.collection.SeqLike {
    public protected *;
}
-keepnames class com.fasterxml.jackson.** { *; }