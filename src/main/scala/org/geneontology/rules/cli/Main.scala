package org.geneontology.rules.cli

import java.io.File
import java.io.FileOutputStream

import scala.collection.JavaConverters._
import scala.io.Source

import org.apache.commons.io.FileUtils
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.reasoner.rulesys.Rule
import org.apache.jena.system.JenaSystem
import org.backuity.clist._
import org.geneontology.jena.OWLtoRules
import org.geneontology.rules.engine.RuleEngine
import org.geneontology.rules.engine.Triple
import org.geneontology.rules.util.Bridge
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.parameters.Imports

object Main extends CliMain[Unit](
  name = "arachne",
  description = "Command-line operations for Arachne RDF rule engine") {

  JenaSystem.init()

  var ontOpt = opt[Option[String]](name = "ontology", description = "OWL ontology to import into reasoning rules")
  var rulesOpt = opt[Option[String]](name = "rules", description = "Jena-syntax rules file to import")
  var exportFileOpt = opt[Option[File]](name = "export", description = "export RDF triples to Turtle file")
  var inferredOnly = opt[Boolean](default = false, name = "inferred-only", description = "export inferred triples only")
  var dataFolder = opt[File](name = "data", description = "folder of RDF data files", default = new File("data"))

  def run: Unit = {
    val ontIRIOpt = ontOpt.map { ontPath => if (ontPath.startsWith("http")) IRI.create(ontPath) else IRI.create(new File(ontPath)) }

    val ontologyRules = ontIRIOpt.map { ontIRI =>
      val manager = OWLManager.createOWLOntologyManager()
      val ontology = time("Loaded ontology from file") {
        manager.loadOntology(ontIRI)
      }

      time("Imported ontology into rules") {
        val jenaRules = OWLtoRules.translate(ontology, Imports.INCLUDED, true, true, false, true)
        Bridge.rulesFromJena(jenaRules)
      }
    }

    val additionalRules = rulesOpt.map { rulesFile =>
      time("Imported Jena rules") {
        Bridge.rulesFromJena(Rule.parseRules(Source.fromFile(new File(rulesFile), "utf-8").mkString).asScala)
      }
    }

    val rules = ontologyRules.getOrElse(Seq.empty) ++ additionalRules.getOrElse(Seq.empty)

    val engine = time("Constructed reasoner from rules") {
      new RuleEngine(rules, false)
    }

    val triples: Set[Triple] = time("Imported data files") {
      val datafiles = FileUtils.listFiles(dataFolder, null, true).asScala
        .filterNot(_.getName == "catalog-v001.xml")
        .filterNot(_.isHidden())
        .filter(_.isFile).toArray
      val dataModel = ModelFactory.createDefaultModel()
      for {
        datafile <- datafiles
      } dataModel.read(datafile.getAbsolutePath)
      dataModel.listStatements.asScala.map(_.asTriple).map(Bridge.tripleFromJena).toSet
    }

    val memory = time("Applied reasoning") {
      engine.processTriples(triples)
    }

    time("Exported data to turtle") {
      val triplesToWrite = if (inferredOnly) memory.facts -- triples else memory.facts
      val outputModel = ModelFactory.createDefaultModel()
      outputModel.add(triplesToWrite.map(Bridge.jenaFromTriple).map(outputModel.asStatement).toSeq.asJava)
      val triplesOutput = exportFileOpt.map(new FileOutputStream(_)).getOrElse(System.out)
      outputModel.write(triplesOutput, "ttl")
      triplesOutput.close()
    }

  }

  def time[T](action: String)(f: => T): T = {
    val s = System.currentTimeMillis
    val res = f
    val time = (System.currentTimeMillis - s) / 1000.0
    println(s"$action in ${time}s")
    res
  }

}
