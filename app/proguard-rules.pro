# Various superfluous inferred dependencies that don't exist on Android
-dontwarn java.beans.*
-dontwarn java.lang.management.*
-dontwarn javax.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn scala.reflect.internal.*
-dontwarn sun.**

# Akka, Scala and Shapeless
-dontwarn akka.actor.CoordinatedShutdown
-dontwarn akka.dispatch.affinity.AffinityPool$IdleStrategy
-dontwarn akka.util.Index
-dontwarn scala.compat.java8.**
-dontwarn shapeless.**
-dontwarn sourcecode.**

# See https://issues.scala-lang.org/browse/SI-5397
-keep class scala.collection.SeqLike {
    public protected *;
}
