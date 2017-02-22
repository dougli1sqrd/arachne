package org.geneontology.rules

import org.apache.jena.rdf.model.ModelFactory
import org.geneontology.jena.OWLtoRules
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.parameters.Imports
import java.util.Date
import org.semanticweb.owlapi.model.IRI
import scala.collection.JavaConversions._
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.typesafe.scalalogging.Logger
import scala.util.Random

object TestRun extends App {

  val dataModel = ModelFactory.createDefaultModel()
  val manager = OWLManager.createOWLOntologyManager()
  dataModel.read(this.getClass.getResourceAsStream("57c82fad00000639.ttl"), "", "ttl")
  //val ontology = manager.loadOntologyFromOntologyDocument(this.getClass.getResourceAsStream("ro-merged.owl"))
  val ontology = manager.loadOntology(IRI.create("http://purl.obolibrary.org/obo/go/extensions/go-lego.owl"))
  val jenaRules = OWLtoRules.translate(ontology, Imports.INCLUDED, true, true, true)
  val rules = jenaRules.map(Bridge.ruleFromJena)
  println(s"Rules: ${rules.size}")
  println("Rule sizes: " + rules.map(_.body.size).toSet)
  println(new Date())
  val engine = new RuleEngine(rules)
  println("Processed rules: ")
  println(new Date())
  val triples = dataModel.listStatements.map(_.asTriple).map(Bridge.tripleFromJena).toVector
  println(s"Starting triples: ${triples.size}")
  engine.processTriples(triples)
  println(new Date())
  println(s"Ending triples: ${engine.workingMemory.size}")

  val reasoner = new GenericRuleReasoner(jenaRules.toList)
  reasoner.setMode(GenericRuleReasoner.FORWARD_RETE)
  val infModel = ModelFactory.createInfModel(reasoner, dataModel)
  println("Jena infer model: " + new Date())
  infModel.prepare()
  println("Jena inferred model: " + new Date())
  println(s"Jena size: ${infModel.size}")
  println("Rule sizes: " + rules.map(_.body.size).toSet)

}