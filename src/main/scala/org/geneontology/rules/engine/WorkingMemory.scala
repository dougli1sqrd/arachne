package org.geneontology.rules.engine

import scala.collection.immutable.Queue
import scala.collection.mutable
import scala.collection.mutable.AnyRefMap

final class WorkingMemory(var asserted: Set[Triple]) {

  var agenda: Queue[Triple] = Queue.empty
  var facts: Set[Triple] = asserted
  var derivations: Map[Triple, List[Derivation]] = Map.empty

  val alpha: mutable.Map[TriplePattern, AlphaMemory] = AnyRefMap.empty
  val beta: mutable.Map[List[TriplePattern], BetaMemory] = AnyRefMap.empty
  beta += (BetaRoot.spec -> BetaRoot.memory)

  def explain(triple: Triple): Set[Explanation] = explainAll(Set(triple))

  private def explainAll(triples: Set[Triple]): Set[Explanation] = {
    val subExplanations = for {
      triple <- triples
    } yield derivations.get(triple) match {
      case None => Set(Explanation(Set(triple), Set.empty))
      case Some(tripleDerivations) => for {
        derivation <- tripleDerivations.toSet[Derivation]
        current = Explanation(Set.empty, Set(derivation.rule))
        subExplanation <- explainAll(derivation.token.triples.toSet).map(e => combine(e, current))
      } yield subExplanation
    }
    cartesianProduct(subExplanations).map(_.reduce(combine))
  }

  private def combine(a: Explanation, b: Explanation): Explanation = Explanation(a.facts ++ b.facts, a.rules ++ b.rules)

  private def cartesianProduct[T](xss: Set[Set[T]]): Set[Set[T]] = xss match {
    case e if e.isEmpty => Set(Set.empty)
    case f =>
      val head = f.head
      val tail = f - head
      for {
        xh <- head
        xt <- cartesianProduct(tail)
      } yield xt + xh
  }

}

final class AlphaMemory(pattern: TriplePattern) {

  var triples: List[Triple] = Nil
  var tripleIndexS: Map[ConcreteNode, Set[Triple]] = Map.empty
  var tripleIndexP: Map[ConcreteNode, Set[Triple]] = Map.empty
  var tripleIndexO: Map[ConcreteNode, Set[Triple]] = Map.empty
  var linkedChildren: List[JoinNode] = Nil

}

final class BetaMemory(val spec: List[TriplePattern], initialLinkedChildren: List[BetaNode]) {

  var tokens: List[Token] = Nil
  var checkRightLink: Boolean = true
  var checkLeftLink: Boolean = false
  //val tokenIndex: mutable.Map[(Variable, ConcreteNode), mutable.Set[Token]] = AnyRefMap.empty
  val tokenIndex: mutable.Map[Variable, mutable.Map[ConcreteNode, List[Token]]] = AnyRefMap.empty
  var linkedChildren: List[BetaNode] = initialLinkedChildren

}

final case class Derivation(token: Token, rule: Rule)

final case class Explanation(facts: Set[Triple], rules: Set[Rule]) {

  override def toString: String = "Facts:\n" + facts.mkString("\n") + "\nRules:\n" + rules.mkString("\n")

}
