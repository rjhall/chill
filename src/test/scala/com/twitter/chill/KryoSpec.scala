/*
Copyright 2012 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.twitter.chill

import org.specs._

import scala.collection.immutable.BitSet
import scala.collection.immutable.ListMap
import scala.collection.immutable.HashMap

import com.twitter.bijection.Bijection

/*
* This is just a test case for Kryo to deal with. It should
* be outside KryoSpec, otherwise the enclosing class, KryoSpec
* will also need to be serialized
*/
case class TestCaseClassForSerialization(x : String, y : Int)

case class TestValMap(val map : Map[String,Double])
case class TestValHashMap(val map : HashMap[String,Double])
case class TestVarArgs(vargs: String*)

object WeekDay extends Enumeration {
 type WeekDay = Value
 val Mon, Tue, Wed, Thu, Fri, Sat, Sun = Value
}

class KryoSpec extends Specification with KryoSerializer {

  noDetailedDiffs() //Fixes issue for scala 2.9

  def rt[T](t : T): T = rt[T](this, t)
  def rt[T](k: KryoSerializer, t : T): T = {
    k.deserialize[T](k.serialize(t.asInstanceOf[AnyRef]))
  }

  "KryoSerializers and KryoDeserializers" should {
    "round trip any non-array object" in {
      val test = List(1,2,"hey",(1,2),
                      ("hey","you"),
                      ("slightly", 1L, "longer", 42, "tuple"),
                      Map(1->2,4->5),
                      0 to 100,
                      (0 to 42).toList, Seq(1,100,1000),
                      Map("good" -> 0.5, "bad" -> -1.0),
                      Set(1,2,3,4,10),
                      BitSet(),
                      BitSet((0 until 1000).map{ x : Int => x*x } : _*),
                      ListMap("good" -> 0.5, "bad" -> -1.0),
                      HashMap("good" -> 0.5, "bad" -> -1.0),
                      TestCaseClassForSerialization("case classes are: ", 10),
                      TestValMap(Map("you" -> 1.0, "every" -> 2.0, "body" -> 3.0, "a" -> 1.0,
                        "b" -> 2.0, "c" -> 3.0, "d" -> 4.0)),
                      TestValHashMap(HashMap("you" -> 1.0)),
                      TestVarArgs("hey", "you", "guys"),
                      implicitly[ClassManifest[(Int,Int)]],
                      Vector(1,2,3,4,5),
                      TestValMap(null),
                      Some("junk"),
                      'hai)
        .asInstanceOf[List[AnyRef]]

      val rtTest = test map { serialize(_) } map { deserialize[AnyRef](_) }
      rtTest must be_==(test)
    }
    "handle manifests" in {
      rt(manifest[Int]) must be_==(manifest[Int])
      rt(manifest[(Int,Int)]) must be_==(manifest[(Int,Int)])
      rt(manifest[Array[Int]]) must be_==(manifest[Array[Int]])
    }
    "handle arrays" in {
      def arrayRT[T](arr : Array[T]) {
        // Array doesn't have a good equals
        rt(arr).toList must be_==(arr.toList)
      }
      arrayRT(Array(0))
      arrayRT(Array(0.1))
      arrayRT(Array("hey"))
      arrayRT(Array((0,1)))
      arrayRT(Array(None, Nil, None, Nil))
    }
    "handle scala singletons" in {
      val test = List(Nil, None)
      //Serialize each:
      rt(test) must be_==(test)
    }
    "Serialize a giant list" in {
      val bigList = (1 to 100000).toList
      val list2 = rt(bigList)
      list2.size must be_==(bigList.size)
      //Specs, it turns out, also doesn't deal with giant lists well:
      list2.zip(bigList).foreach { tup => tup._1 must be_==(tup._2) }
    }
    "Handle scala enums" in {
       WeekDay.values.foreach { v =>
         rt(v) must be_==(v)
       }
    }
    "use bijections" in {
      import KryoImplicits.toRich

      implicit val bij = Bijection.build[TestCaseClassForSerialization, (String,Int)] { s =>
        (s.x, s.y) } { tup => TestCaseClassForSerialization(tup._1, tup._2) }

      val k = new KryoSerializer { override def getKryo = {
        super.getKryo.forClassViaBijection[TestCaseClassForSerialization, (String,Int)]
      }}
      rt(k, TestCaseClassForSerialization("hey", 42)) must be_==(TestCaseClassForSerialization("hey", 42))
    }
    "use java serialization" in {
      import KryoImplicits.toRich

      val k = new KryoSerializer { override def getKryo = {
        super.getKryo.javaForClass[TestCaseClassForSerialization]
      }}
      rt(k, TestCaseClassForSerialization("hey", 42)) must be_==(TestCaseClassForSerialization("hey", 42))
    }
  }
}
