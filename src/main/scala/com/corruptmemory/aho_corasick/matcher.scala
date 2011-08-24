package com.corruptmemory.aho_corasick

import scalaz._
import Scalaz._
import scala.collection.mutable.{Queue => MQueue, LinkedList}

class AhoCorasickBuilder[T](charMap:Char => Char = _.toLower) {
  import AhoCorasickBuilder._
  var rootGoto:Goto = new Goto with RootGoto

  trait RootGoto {
    self:Goto =>
    override def goto(c:Char):Option[Goto] = next.get(c).map(_.data) orElse (some(self))
  }

  class Goto {
    val next:Node[Goto] = Node(this)
    var outputs:Option[LinkedList[Data[T]]] = none
    var fail:Option[Goto] = none
    def goto(c:Char):Option[Goto] = next.get(c).map(_.data)
    def failToString:String = {
      fail.fold(none = "<>",
                some = s => "<"+s.toString+">")
    }
    override def toString:String = {
      "goto(%d,%s,%s,%s)".format(outputs,failToString,next.entries.map(_.map(neToString(_)).mkString("[",",","]")))
    }
  }

  class AhoCorasick(rootGoto:Goto) {
    def find(in:String):Seq[Match[T]] = {
      var state = rootGoto
      val builder = Vector.newBuilder[Match[T]]
      in.map(charMap(_)).zipWithIndex.foreach {
        case (c,i) => {
          while (!state.goto(c).isDefined) { state = state.fail.get }
          state = state.goto(c).get
          state.outputs.foreach {
            s => {
              builder ++= s.toSeq.map(x => Match(i-x.string.length+1,x.string,in.slice(i-x.string.length+1,i+1),x.data))
            }
          }
        }
      }
      builder.result
    }
  }

  def +=(in:Data[T]):AhoCorasickBuilder[T] = {
    if (!in.string.isEmpty) {
      val target = in.string.map(charMap(_)).foldLeft(rootGoto) {
        (g,c) => {
          g.next.get(c).fold(none = {
                               val n = (new Goto).next
                               g.next += c -> n
                               n.data
                             },
                             some = s => s.data)
        }
      }
      target.outputs.fold(none = target.outputs = some(LinkedList(in)),
                          some = s => s.+:(in))
    }
    this
  }

  def neToString(ne:NodeEntry[Goto]):String =
    "{%s: %s}".format(ne.char,ne.node.data.toString)

  def build():AhoCorasick = {
    val queue = MQueue[Goto]()
    rootGoto.next.entries.foreach {
        _.foreach {
        (s:NodeEntry[Goto]) => {
          s.node.data.fail = some(rootGoto)
          queue += s.node.data
        }
      }
    }
    while (!queue.isEmpty) {
      val r = queue.dequeue()
      r.next.entries.foreach {
        _.foreach {
          (xx:NodeEntry[Goto]) => {
            // debugNE(xx)
            val a:Char = xx.char
            val s:Goto = xx.node.data
            queue += s
            var state = r.fail.get
            while (!state.goto(a).isDefined) {
              state = state.fail.get
            }
            val down = state.goto(a).get
            s.fail = some(down)
            down.outputs.foreach {
              dos => {
                s.outputs.fold(none = s.outputs = some(dos),
                               some = s1 => s.outputs = some(s1.++:(dos)))
              }
            }
          }
        }
      }
    }
    val result = new AhoCorasick(rootGoto)
    rootGoto = new Goto with RootGoto
    result
  }
}

object AhoCorasickBuilder {
  case class Data[T](string:String,data:T)
  implicit def toData[T](x:(String,T)):Data[T] = Data(x._1,x._2)
  def apply[T](in:Seq[Data[T]], charMap:Char => Char = _.toLower):AhoCorasickBuilder[T] =
    in.foldLeft(new AhoCorasickBuilder[T](charMap))((s,v) => s += v)
}