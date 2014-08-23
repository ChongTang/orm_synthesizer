package edu.virginia.cs

import edu.virginia.cs.Framework._
import edu.virginia.cs.Framework.DBSpecification
import edu.virginia.cs.Framework.DBImplementation
import edu.virginia.cs.Framework.DBBenchmark
import edu.virginia.cs.Framework.DBBenchmarkResult
import edu.mit.csail.sdg.alloy4.A4Reporter
import edu.mit.csail.sdg.alloy4.Err
import edu.mit.csail.sdg.alloy4.ErrorWarning
import edu.mit.csail.sdg.alloy4compiler.ast.Command
import edu.mit.csail.sdg.alloy4compiler.ast.ExprVar
import edu.mit.csail.sdg.alloy4compiler.parser.CompUtil
import edu.mit.csail.sdg.alloy4compiler.parser.Module
import edu.mit.csail.sdg.alloy4compiler.translator.A4Options
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution
import edu.mit.csail.sdg.alloy4compiler.translator.TranslateAlloyToKodkod
import java.io.IOException
import java.io.File
import edu.virginia.cs.Synthesizer.SmartBridge
import edu.virginia.cs.Analyzer.DBRunBenchmark
import edu.virginia.cs.Synthesizer.FileOperation
import edu.virginia.cs.Synthesizer.AlloyOMToAlloyDM
import java.util.HashMap
import java.util.ArrayList
import edu.virginia.cs.Synthesizer.CodeNamePair
import edu.virginia.cs.Synthesizer.Sig
import edu.virginia.cs.Synthesizer.ORMParser
import edu.virginia.cs.Synthesizer.Types.ObjectSpec
import edu.virginia.cs.Synthesizer.Types.ObjectSet
import java.io.FileOutputStream
import java.io.PrintWriter
import edu.virginia.cs.Synthesizer.LoadSynthesizer
import edu.virginia.cs.Synthesizer.Types.ObjectOfDM
import edu.virginia.cs.Synthesizer.Types.AbstractLoad
import edu.virginia.cs.Synthesizer.Types.AbstractQuery
import edu.virginia.cs.Synthesizer.Types.AbstractQuery.Action
import edu.virginia.cs.Synthesizer.Types.ConcreteQuery
import edu.virginia.cs.Synthesizer.Types.SpecializedQuery
import edu.virginia.cs.Synthesizer.Types.ConcreteLoad
import edu.virginia.cs.Synthesizer.SolveAlloyDM
import edu.virginia.cs.Synthesizer.Types.FormalAbstractLoadSet
import edu.virginia.cs.Synthesizer.Types.DBFormalAbstractMeasurementFunctionSet
import edu.virginia.cs.Framework.DBFormalImplementation
import edu.virginia.cs.Synthesizer.Types.DBFormalConcreteMeasurementFunctionSet
import scala.collection.JavaConversions._
import java.text.NumberFormat
import java.text.ParsePosition
import edu.virginia.cs.Synthesizer.PrintOrder
import edu.virginia.cs.Synthesizer.Types.DBFormalAbstractMeasurementFunction
import edu.virginia.cs.Synthesizer.Types.DBFormalConcreteTimeMeasurementFunction
import edu.virginia.cs.Synthesizer.Types.DBFormalConcreteSpaceMeasurementFunction
import edu.virginia.cs.Synthesizer.Types.DBFormalAbstractTimeMeasurementFunction
import edu.virginia.cs.Synthesizer.Types.DBFormalAbstractSpaceMeasurementFunction
import java.io.FileNotFoundException

class DBTrademaker extends TrademakerFramework {

  var solveDM: SolveAlloyDM = null

  var schemas = new HashMap[String, HashMap[String, ArrayList[CodeNamePair[String]]]]()
  var parents = new HashMap[String, ArrayList[CodeNamePair[String]]]()
  var reverseTAss = new HashMap[String, ArrayList[CodeNamePair[String]]]()
  var foreignKeys = new HashMap[String, ArrayList[CodeNamePair[String]]]()
  var association = new HashMap[String, HashMap[String, CodeNamePair[String]]]()
  var primaryKeys = new HashMap[String, ArrayList[CodeNamePair[String]]]()
  var fields = new HashMap[String, ArrayList[CodeNamePair[String]]]()
  var fieldsTable = new HashMap[String, ArrayList[CodeNamePair[String]]]()
  var allFields = new HashMap[String, ArrayList[String]]()
  var fieldType = new HashMap[String, ArrayList[CodeNamePair[String]]]()

  /**
   *     parents.put(fImpFileName, parser.getParents());
   * reverseTAss.put(fImpFileName, parser.getReverseTAssociate());
   * foreignKeys.put(fImpFileName, parser.getForeignKey());
   * association.put(fImpFileName, parser.getAssociation());
   * primaryKeys.put(fImpFileName, parser.getPrimaryKeys());
   * fields.put(fImpFileName, parser.getFields());
   * allFields.put(fImpFileName, parser.getallFields());
   * fieldsTable.put(fImpFileName, parser.getFieldsTable());
   */

  //  var ids: ArrayList[String] = new ArrayList[String]()
  //  var associations: ArrayList[String] = null
  //  var typeList: HashMap[String, String] = null
  //  var sigs: ArrayList[Sig] = null
  var intScope: Integer = 6

  //	type ImplementationType = String
  //  override type BenchmarkType = String
  type SpecificationType >: DBSpecification
  type ImplementationType >: DBImplementation
  type MeasurementFunctionSetType >: DBBenchmark
  type FormalSpecificationType >: DBFormalSpecification
  type FormalImplementationType >: DBFormalImplementation
  type FormalAbstractBenchmarkType >: DBFormalAbstractBenchmark
  type FormalConcreteBenchmarkType >: DBFormalConcreteBenchmark

  def mySynthesizer(spec: SpecificationType): (List[Prod[ImplementationType, MeasurementFunctionSetType]]) = {

    var fSpec: FormalSpecificationType = mySFunction(spec)

    var fImpl: List[FormalImplementationType] = myCFunction(fSpec)

    var cImpl: List[ImplementationType] = myIFunctionHelper(fImpl)

    var fAbsMF: FormalAbstractMeasurementFunctionSet = myLFunction(fSpec)

    var fConMF: List[FormalConcreteMeasurementFunctionSet] = myTFunction(fAbsMF)(cImpl)

    var bms: List[MeasurementFunctionSetType] = myBFunctionHelper(fConMF)

    combine[ImplementationType, MeasurementFunctionSetType](cImpl)(bms)
  }

  def myRunBenchmark(prod: Prod[ImplementationType, MeasurementFunctionSetType]): Prod[ImplementationType, MeasurementResultSetType] = {
    println("This is myRunBenchmark function")

    var prodCasted = prod.asInstanceOf[Prod[DBImplementation, DBBenchmark]]

    var bm = new DBRunBenchmark(fst[DBImplementation, DBBenchmark](prodCasted).getImPath, snd[DBImplementation, DBBenchmark](prodCasted))

    Pair[ImplementationType, MeasurementResultSetType](fst(prod), new DBBenchmarkResult(bm.insertTime(), bm.selectTime()))
  }

  // analyze and tradespace are already defined in Tradespace specification
  // we can call "tradespace" function to synthesize implementation and benchmark
  var myTradespace: Tradespace = new Build_Tradespace(mySynthesizer _, myRunBenchmark _)
  //).asInstanceOf[Any => List[Prod[Any, Any]]]
  //).asInstanceOf[Prod[Any, Any] => Prod[Any, Any]]
  // here we get the tradespace function and analyze function
  def tradespaceFunction = tradespace(myTradespace) // fun: (SpecificationType => List[Prod[ImplementationType, BenchmarkResultType]])
  var analyzeFunction = analyze(myTradespace)

  /**
   * Stub out ParetoFront
   */

  def myParetoFilter(list: List[Prod[ImplementationType, MeasurementResultSetType]]): List[Prod[ImplementationType, MeasurementResultSetType]] = {
    println("This is myParetoFilter function")
    Nil[Prod[ImplementationType, MeasurementResultSetType]]()
  }

  var myParetoFront: ParetoFront = new Build_ParetoFront(myTradespace, myParetoFilter)

  // returns the pareto function we defined in the specification
  def myParetoFrontFunction = paretoFront(myParetoFront)

  def getParetoResults(mySpec: SpecificationType) = myParetoFrontFunction(mySpec)

  /**
   * Define functions for Trademaker in specification
   */
  def myCFunction(fSpec: FormalSpecificationType): List[FormalImplementationType] = {
    println("This is myCFunction function")

    /*
     * get specification file path
     * calculate solution folder based on specification file path
     * call smartbridge() function to synthesize formal implementations
     * scan solution folder to get implementations
     */
    var specPath: String = fSpec.asInstanceOf[DBFormalSpecification].getSpec

    var solFolder: String = specPath.substring(0, specPath.lastIndexOf("/"))
    var alloyOMName = specPath.substring(specPath.lastIndexOf("/") + 1, specPath.lastIndexOf("."))
    solFolder = solFolder + File.separator + alloyOMName + File.separator + "ImplSolution"
    if (!new File(solFolder).exists()) {
      new File(solFolder).mkdirs()
    }

    // get mapping run file
    var mappingRun: String = FileOperation.getMappingRun(specPath)
    // call smartBridge
    new SmartBridge(solFolder, mappingRun, 100000);
    // delete mappingRun file
    new File(mappingRun).delete()

    // scan solution folder, and get all the solutions
    var solFiles: Array[File] = new java.io.File(solFolder).listFiles.filter(_.getName.endsWith(".xml"))
    //     * var benchMarkFolder = solFolder+File.separator+"Benchmark"
    var imFile: File = null
    //     * var insertBMFile:File = null
    //     * var size:Integer = solFiles.length
    var implList: List[FormalImplementationType] = Nil[FormalImplementationType]()

    for (file <- solFiles) {
      var imFileName = file.getName()
      var dbImpl: DBFormalImplementation = new DBFormalImplementation()
      dbImpl.setImp(file.getAbsolutePath)
      dbImpl.setSigs(fSpec.asInstanceOf[DBFormalSpecification].getSigs)
      dbImpl.setAssociationsForCreateSchemas(fSpec.asInstanceOf[DBFormalSpecification].getAssociations)
      dbImpl.setTypeMap(fSpec.asInstanceOf[DBFormalSpecification].getTypeMap)
      dbImpl.setIds(fSpec.asInstanceOf[DBFormalSpecification].getIds)
      implList = new Cons[FormalImplementationType](dbImpl, implList)
    }
    implList
    //    var myPair: FormalImplementationType = new DBFormalImplementation()
    //    var emptyList = Nil[FormalImplementationType]()
    //    Cons[FormalImplementationType](myPair, emptyList)
  }

  def myAFunction(fImp: FormalImplementationType): FormalSpecificationType = {
    println("This is myAFunction function")
    ""
  }

  def myLFunction(fSpec: FormalSpecificationType): FormalAbstractMeasurementFunctionSet /*List[AbstractLoad]*/ = {
    // generate two abstract load objects:  insert and select
    // create two measurement functions, one for time, one for space 
    // wrap them in a FormalAbstractMeasurementFunctionSet 

    var absLoads: FormalAbstractLoadSet = generateFormalAbstractLoadSet(fSpec);
    var absTimeMeasurementFunction = new DBFormalAbstractTimeMeasurementFunction(absLoads.getInsLoad(), absLoads.getSelLoad())
    var absSpaceMeasurementFunction = new DBFormalAbstractSpaceMeasurementFunction(absLoads.getInsLoad())
    new DBFormalAbstractMeasurementFunctionSet(absTimeMeasurementFunction, absSpaceMeasurementFunction)
  }

  def recursiveDelete(f: File): Boolean = {
    if (f.isDirectory()) {
      for (c <- f.listFiles())
        recursiveDelete(c);
    }
    if (!f.delete()) {
      //      throw new FileNotFoundException("Failed to delete file: " + f)
    }
    true
  }

  def generateFormalAbstractLoadSet(fSpec: FormalSpecificationType): FormalAbstractLoadSet = {

    // initialize two empty abstract loads objects, to contain insert and select queries
    // for each object solution to fSpec
    //     - generate abstract insert and select queries,
    //			and add them to the corresponding abstract load objects 
    // package up the two abstract load objects in a FormalAbstractLoadSet object and return the result

    // initialize two empty abstract loads objects, to contain insert and select queries    
    var insAbsLoad = new AbstractLoad()
    var selAbsLoad = new AbstractLoad()

    // for each object solution to fSpec
    var objSpec = genObjSpec(fSpec)

    // construct path to solution folder
    var specPath = objSpec.getSpecPath()
    var lenOfExtension = "_dm.als".length()
    var objectSolFolder = specPath.substring(0, specPath.length() - lenOfExtension)
    //    var specName = specPath.substring(specPath.lastIndexOf(File.separator) + 1, specPath.indexOf("_"))
    objectSolFolder = objectSolFolder + File.separator + "TestSolutions"
    recursiveDelete(new File(objectSolFolder))
    //        new File(objectSolFolder).delete()
    new File(objectSolFolder).mkdirs()
    // create solution folder
    //    if (!new File(objectSolFolder).exists()) {
    //      new File(objectSolFolder).mkdirs()
    //    }

    // call objects generator
    var loadSynthesizer = new LoadSynthesizer()
    loadSynthesizer.genObjects(specPath, objectSolFolder)

    /*
     * get solutions to alloy spec (stored as XML files)
     * 
     * for each such solution ("object")
     *    * generate two abstract queries
     *    * add queries to relevant abstract load objects
     */

    // iterate all objects
    var objectFiles: Array[File] = new java.io.File(objectSolFolder).listFiles.filter(_.getName.endsWith(".xml"))
    var file: File = null
    var objects: ObjectSet = new ObjectSet()

    var insQuerySet: ArrayList[AbstractQuery] = new ArrayList()
    var selQuerySet: ArrayList[AbstractQuery] = new ArrayList()

    for (file <- objectFiles) {
      var objectFileName = file.getName()
      var singleObject = new ObjectOfDM(file.getAbsolutePath())

      var insQuery: AbstractQuery = new AbstractQuery(AbstractQuery.Action.INSERT, singleObject)
      var selQuery: AbstractQuery = new AbstractQuery(AbstractQuery.Action.SELECT, singleObject)

      insQuerySet.add(insQuery)
      selQuerySet.add(selQuery)

    }
    insAbsLoad.setQuerySet(insQuerySet)
    selAbsLoad.setQuerySet(selQuerySet)

    var fAbsLoadSet: FormalAbstractLoadSet = new FormalAbstractLoadSet(insAbsLoad, selAbsLoad)
    fAbsLoadSet
  }

  /*
   *  purpose is to convert a given abstract measurement function (set of insert or select abstract queries) into a concrete measurement
   *  function, specialized to a particular implementation.
  */

  // Chong: changed FormalAbstractMeasurementFunction to FormalAbstractMeasurementFunctionSet
  //  def getConcreteMeasurementFunctions(absMF: FormalAbstractMeasurementFunction, impls: ArrayList[DBFormalImplementation]): ArrayList[ConcreteMeasurementFunc] = {
  def getConcreteMeasurementFunctionSets(absMF: DBFormalAbstractMeasurementFunctionSet, impls: ArrayList[DBImplementation]): ArrayList[DBFormalConcreteMeasurementFunctionSet] = {
    // iterate over all abstract measurement functions and convert them all to concrete measurement functions
    var returnValue: ArrayList[DBFormalConcreteMeasurementFunctionSet] = new ArrayList()
    var i: DBFormalImplementation = null
    var it = impls.iterator()
    while (it.hasNext()) {
      returnValue.add(getConcreteMeasurementFunctionSet(absMF, it.next()))
    }
    returnValue
  }

  //  def getConcreteMeasurementFunction(absMF: DBFormalAbstractMeasurementFunctionSet, impl: DBFormalImplementation): ConcreteMeasurementFunc = {
  def getConcreteMeasurementFunctionSet(absMF: DBFormalAbstractMeasurementFunctionSet, impl: DBImplementation): DBFormalConcreteMeasurementFunctionSet = {
    var concMFSet: DBFormalConcreteMeasurementFunctionSet = new DBFormalConcreteMeasurementFunctionSet()

    // for time
    var tmf: DBFormalAbstractMeasurementFunction = absMF.getTmf()
    var tmfALoads: ArrayList[AbstractLoad] = tmf.getLoads()
    var insAL: AbstractLoad = tmfALoads.get(0)
    var selAL: AbstractLoad = tmfALoads.get(1)
    var insCL = convert(insAL, impl)
    var selCL = convert(selAL, impl)
    var ctmf: DBFormalConcreteTimeMeasurementFunction = new DBFormalConcreteTimeMeasurementFunction(insCL, selCL)
    concMFSet.setCtmf(ctmf)

    // chong: duplicated code here
    // for space
    //    var smf: DBFormalAbstractMeasurementFunction = absMF.getSmf()
    //    var smfALoads: ArrayList[AbstractLoad] = smf.getLoads()
    //    insAL = smfALoads.get(0)
    //    insCL = convert(insAL, impl)
    var csmf: DBFormalConcreteSpaceMeasurementFunction = new DBFormalConcreteSpaceMeasurementFunction(insCL)
    concMFSet.setCsmf(csmf)
    concMFSet.setImpl(impl)

    // return concMFSet
    concMFSet
  }

  def convert(absl: AbstractLoad, impl: DBImplementation): ConcreteLoad = {
    // iterate absl
    // get abstract queries in absl
    // convert abstract queries to concrete queryies
    // add concrete queries to concrete loads
    // return it

    //    var allInsertStmts: HashMap[String, HashMap[Integer, String]] = new HashMap[String, HashMap[Integer, String]]();

    var cl: ConcreteLoad = new ConcreteLoad()

    var absqs = absl.getQuerySet()
    var it = absqs.iterator()
    while (it.hasNext()) {
      var absq = it.next()
      if (absq.getOodm().getObjectPath().contains("218")) {
        println(absq.getOodm().getObjectPath())
      }
      var cq = convertQuery(absq, impl)
      cl.getQuerySet().add(cq)
    }

    // print out all statements to file 
    var implPath = impl.getImPath
    var pathBase = implPath.substring(0, implPath.lastIndexOf(File.separator))
    var implFileName = implPath.substring(implPath.lastIndexOf(File.separator) + 1, implPath.lastIndexOf("."))
    pathBase += File.separator + "TestCases"
    if (!new File(pathBase).exists()) {
      new File(pathBase).mkdirs();
    }
    var selectPath = pathBase + File.separator + implFileName + "_select.sql"
    var insertPath = pathBase + File.separator + implFileName + "_insert.sql"

    var insertFile: File = new File(insertPath)
    if (!insertFile.exists()) {
      insertFile.createNewFile()
    }
    var selectFile: File = new File(selectPath)
    if (!selectFile.exists()) {
      selectFile.createNewFile()
    }

    var insertPw: PrintWriter = new PrintWriter(insertFile)
    var selectPw: PrintWriter = new PrintWriter(selectFile)

    var printOrder = PrintOrder.getOutPutOrders(pathBase)

    // iterate the list to add each element to hashmap
    var allInsertStmts = new ArrayList[HashMap[String, HashMap[Integer, String]]]();
    var allSelectStmts = new ArrayList[HashMap[String, ArrayList[String]]]();
    for (elem <- cl.getQuerySet()) {
      if (elem.getAction() == Action.INSERT) {
        var sq = elem.getSq()
        var sqInOneObject = sq.getInsertStmtsInOneObject()
        allInsertStmts.add(sqInOneObject)
      } else if (elem.getAction() == Action.SELECT) {
        var sq = elem.getSq()
        allSelectStmts.add(sq.getSelectStmtsInOneObject())
      }
    }

    // print out insert statements
    for (s <- printOrder) {
      for (insertS <- allInsertStmts) {
        var mapIt = insertS.iterator
        while (mapIt.hasNext) {
          var elem = mapIt.next
          if (elem._1.equalsIgnoreCase(s)) {
            // 
            var tmp = elem._2
            var tmpIt = tmp.iterator
            while (tmpIt.hasNext) {
              var stmt = tmpIt.next
              println(stmt._2)
            }
          }
        }
      }
    }

    //print out all select statements
    for (s <- printOrder) {
      for (selectS <- allSelectStmts) {
        var mapIt = selectS.iterator
        while (mapIt.hasNext) {
          var elem = mapIt.next
          if (elem._1.equalsIgnoreCase(s)) {
            selectPw.println(elem._2)
          }
        }
      }
    }

    insertPw.close()
    selectPw.close()

    cl.setInsertPath(insertPath)
    cl.setSelectPath(selectPath)
    cl
  }

  def convertQuery(absq: AbstractQuery, impl: DBImplementation): ConcreteQuery = {
    // get the action of absq ; a = absq.getAction()
    /*
     *  if a==Insert
     *  	* generateInsert()
     *   else generateSelect()
     */
    var cq = new ConcreteQuery()
    var a = absq.getAction()
    if (a == Action.INSERT) {
      cq.setAction(Action.INSERT)
      cq.setSq(specializeInsertQuery(absq, impl))
    } else {
      cq.setAction(Action.SELECT)
      cq.setSq(specializeSelectQuery(absq, impl))
    }
    cq
  }

  def specializeInsertQuery(absq: AbstractQuery, impl: DBImplementation): SpecializedQuery = {
    // allInstances here contains all instances in a single object file, which is got by parse the object file
    // some fields may have more than one instance
    // allInstances is a hashmap: HashMap[String, HashMap[String, ArrayList[CodeNamePair[String>>>>
    // HashMap[tableName, HashMap[instanceName, fields_value_pairs]]
    var allInstances = absq.getOodm().parseDocument()
    var it = allInstances.iterator

    var field_part: String = "";
    var value_part: String = "";
    var fTables: ArrayList[String] = new ArrayList()
    var asss: ArrayList[HashMap[String, CodeNamePair[String]]] = new ArrayList[HashMap[String, CodeNamePair[String]]]()
    var ass: HashMap[String, CodeNamePair[String]] = null

    var allInsertStmts: HashMap[String, HashMap[Integer, String]] = new HashMap[String, HashMap[Integer, String]]();
    /**
     * Prepare output file by implPath
     */

    var implPath = impl.getImPath
    var insertPath = implPath.substring(0, implPath.lastIndexOf(File.separator))
    var implFileName = implPath.substring(implPath.lastIndexOf(File.separator) + 1, implPath.lastIndexOf("."))
    insertPath += File.separator + "TestCases"
    if (!new File(insertPath).exists()) {
      new File(insertPath).mkdirs();
    }
    insertPath += File.separator + implFileName + "_insert.sql"

    while (it.hasNext) {
      var instances = it.next
      var element: String = instances._1 // get key. tableName
      var instancesIt = instances._2.iterator
      while (instancesIt.hasNext) {
        var singleInstance = instancesIt.next
        var instanceName: String = singleInstance._1
        var fieldValuePairs = singleInstance._2
        // key is tableName, and value is fields in the table
        var dbScheme = impl.getDataSchemas // HashMap[String, ArrayList[CodeNamePair[String]]], 
        // which table the element class will be
        var reverseTAss = impl.getReverseTAssociate
        var goToTable = getTableNameByElement(reverseTAss, element) // null if not found
        if (goToTable != null) {
          // there is no t_association information for this element
          // which indicates it's a association table
          var id: String = getPrimaryKeyByTableName(dbScheme, goToTable)
          var id_value: String = getFieldValue(fieldValuePairs, id)

          field_part = "";
          value_part = "";

          var allAboutOMClass: ArrayList[CodeNamePair[String]] = dbScheme.get(goToTable)
          for (pair <- allAboutOMClass if pair.getFirst().equalsIgnoreCase("fields")) {
            var fieldName: String = pair.getSecond()
            var fieldValue = getFieldValue(fieldValuePairs, fieldName)
            if (fieldValue != null) {
              field_part += "`" + fieldName + "`,";
              value_part += fieldValue + ",";
            } else {
              if (fieldName.equalsIgnoreCase("DType")) {
                fieldValue = "'" + element + "'"
                field_part += "`" + fieldName + "`,"
                value_part += fieldValue + ","
              } else {
                var isFKey = isForeignKey(dbScheme, goToTable, fieldName)
                if (isFKey) {
                  fTables = getTablesByPrimaryKey(impl.getPrimaryKeys, fieldName)
                  asss.clear()
                  for (fTable <- fTables) {
                    ass = getAssByKey(dbScheme, element, fTable)
                    if (ass != null) {
                      asss.add(ass)
                    }
                  }
                  // now we have the associations, still need to find out which association to search
                  // first get the primary key of this table
                  // then get the

                  for (tmp_ass <- asss) {
                    var tmp_ass_It = tmp_ass.iterator
                    while (tmp_ass_It.hasNext) {
                      var entry = tmp_ass_It.next
                      var para: String = null
                      if (entry._2.getFirst().equalsIgnoreCase(id)) {
                        para = entry._2.getSecond()
                      } else {
                        para = entry._2.getFirst()
                      }
                      var pKeyOfPara = getPrimaryKeyByTableName(dbScheme, para)
                      var fValue = getForeignKeyValue(allInstances.get(entry._1), id_value, id, pKeyOfPara)
                      field_part += "`" + fieldName + "`,"
                      value_part += fValue + ","
                    }
                  }
                }
              }
            }
          }
          field_part = field_part.substring(0, field_part.length() - 1)
          value_part = value_part.substring(0, value_part.length() - 1)
          var stmt: String = "INSERT INTO `" + goToTable + "` (" + field_part + ") VALUES (" + value_part + ");"
          stmt += "FLUSH TABLES;";
          // add statments
          if (!dataSchemaHasInsertStatement(allInsertStmts, goToTable, Integer.valueOf(id_value))) {
            addInsertStmtIntoDataSchema(allInsertStmts, goToTable, stmt, Integer.valueOf(id_value));
          }
        }
      }
    }
    var sQueries: SpecializedQuery = new SpecializedQuery()
    sQueries.setInsertStmtsInOneObject(allInsertStmts)
    sQueries
  }

  def addInsertStmtIntoDataSchema(allInserts: HashMap[String, HashMap[Integer, String]],
    goToTable: String, stmt: String, idValue: Integer) = {
    var contains: Boolean = allInserts.containsKey(goToTable)
    if (!contains) {
      allInserts.put(goToTable, new HashMap[Integer, String])
    }
    allInserts.get(goToTable).put(idValue, stmt)
  }

  def dataSchemaHasInsertStatement(allInserts: HashMap[String, HashMap[Integer, String]],
    goToTable: String, idValue: Integer): Boolean = {
    if (allInserts.containsKey(goToTable)) {
      if (allInserts.get(goToTable).containsKey(idValue)) {
        return true
      }
    }
    false
  }

  def getAssByKey(scheme: HashMap[String, ArrayList[CodeNamePair[String]]],
    pTable: String, fTable: String): HashMap[String, CodeNamePair[String]] = {
    var ass_map: HashMap[String, CodeNamePair[String]] = new HashMap[String, CodeNamePair[String]]()
    var src: String = "";
    var dst: String = "";
    var ass: String = "";

    var schemeIt = scheme.iterator
    while (schemeIt.hasNext) {
      var table = schemeIt.next
      var fields = table._2
      for (pair <- fields) {
        if (pair.getFirst().equalsIgnoreCase("src")) {
          if (pair.getSecond().equalsIgnoreCase(pTable)) {
            src = pTable;
          }
          if (pair.getSecond().equalsIgnoreCase(fTable)) {
            src = fTable;
          }
        }
        if (pair.getFirst().equalsIgnoreCase("dst")) {
          if (pair.getSecond().equalsIgnoreCase(pTable)) {
            dst = pTable;
          }
          if (pair.getSecond().equalsIgnoreCase(fTable)) {
            dst = fTable;
          }
        }
      }
      if (src.length() > 0 && dst.length() > 0) {
        ass = table._1;
        var pair: CodeNamePair[String] = new CodeNamePair[String](src, dst)
        ass_map.put(ass, pair);
        return ass_map
      }
    }
    null
  }

  def getTablesByPrimaryKey(pKeys: ArrayList[CodeNamePair[String]], primaryKey: String): ArrayList[String] = {
    var tables: ArrayList[String] = new ArrayList[String]();
    for (pair <- pKeys) {
      if (pair.getSecond().equalsIgnoreCase(primaryKey)) {
        tables.add(pair.getFirst());
      }
    }
    return tables
  }

  def getForeignKeyValue(instance: HashMap[String, ArrayList[CodeNamePair[String]]],
    keyValue: String, srcDst: String, srcDst1: String): String = {
    var value: String = null
    var instanceIt = instance.iterator
    while (instanceIt.hasNext) {
      var entryValue = instanceIt.next._2
      for (pair <- entryValue) {
        if (pair.getFirst().split("_")(1).equalsIgnoreCase(srcDst1)) {
          var intValue: Integer = Integer.valueOf(pair.getSecond()).intValue();
          var power = scala.math.pow(2, (intScope - 1))
          intValue = intValue + power.intValue() + 1;
          return String.valueOf(intValue)
        }
      }
    }
    return value
  }

  def isForeignKey(scheme: HashMap[String, ArrayList[CodeNamePair[String]]], table: String, field: String): Boolean = {
    for (pair <- scheme.get(table)) {
      if (pair.getFirst().equalsIgnoreCase("foreignKey")) {
        if (pair.getSecond().equalsIgnoreCase(field)) {
          return true
        }
      }
    }
    return false
  }

  def getFieldValue(fieldValues: ArrayList[CodeNamePair[String]], field: String): String = {
    var value: String = null
    var pair: CodeNamePair[String] = null
    for (pair <- fieldValues) {
      if (pair.getFirst().split("_")(1).equalsIgnoreCase(field)) {
        var tmp: String = pair.getSecond();
        if (isNumeric(tmp)) {
          var intValue = Integer.valueOf(tmp).intValue()
          var power = scala.math.pow(2, (intScope - 1))
          intValue = intValue + power.intValue() + 1
          value = String.valueOf(intValue)
          return value
        }
      }
    }
    null
  }

  def isNumeric(str: String): Boolean = {
    var formatter: NumberFormat = NumberFormat.getInstance()
    var pos: ParsePosition = new ParsePosition(0)
    formatter.parse(str, pos);
    return str.length() == pos.getIndex()
  }

  def getPrimaryKeyByTableName(dbScheme: HashMap[String, ArrayList[CodeNamePair[String]]], tableName: String): String = {
    var table: ArrayList[CodeNamePair[String]] = dbScheme.get(tableName);
    var pair: CodeNamePair[String] = null
    for (pair <- table) {
      if (pair.getFirst().equalsIgnoreCase("primaryKey")) {
        return pair.getSecond()
      }
    }
    return ""
  }

  // looks up reverse t_associate data structure to find a target table for each object element, e.g. a class instance or an association
  def getTableNameByElement(reverseTAss: ArrayList[CodeNamePair[String]], element: String): String = {
    var elem: CodeNamePair[String] = null
    for (elem <- reverseTAss) {
      if (elem.getFirst().equalsIgnoreCase(element)) {
        return elem.getSecond()
      }
    }
    return null
  }

  def isAssociation(sigs: ArrayList[Sig], element: String): Boolean = {
    for (sig <- sigs) {
      if (sig.getCategory() == 1 && sig.getSigName().equalsIgnoreCase(element)) {
        return true;
      }
    }
    false
  }

  def getParent(sigs: ArrayList[Sig], element: String): String = {
    for (sig <- sigs) {
      if (sig.getCategory() == 0) {
        if (sig.isHasParent()) {
          return sig.getParent()
        }
      }
    }
    null
  }

  def isPrimaryKeys(pKeys: ArrayList[CodeNamePair[String]], table: String, field: String): Boolean = {
    for (s <- pKeys) {
      if (s.getFirst().equalsIgnoreCase(table) && s.getSecond().equalsIgnoreCase(field)) {
        return true;
      }
    }
    return false;
  }

  def specializeSelectQuery(absq: AbstractQuery, impl: DBImplementation): SpecializedQuery = {
    var selectPart = ""
    var fromPart = ""
    var wherePart = ""
    var allAboutOMClass: ArrayList[CodeNamePair[String]] = null
    var allSelectStmts: HashMap[String, ArrayList[String]] = new HashMap[String, ArrayList[String]]()

    var instance = absq.getOodm().parseDocument()
    var instanceIt = instance.iterator
    while (instanceIt.hasNext) {
      var instanceEntry = instanceIt.next
      var element = instanceEntry._1
      var instance = instanceEntry._2
      var isAss = isAssociation(impl.getSigs, element)
      if (!isAss) {
        var instanceIt = instance.iterator
        while (instanceIt.hasNext) {
          var singleInstance = instanceIt.next
          var fieldValuePairs = singleInstance._2

          selectPart = "SELECT ";
          fromPart = " FROM ";
          wherePart = " WHERE ";

          var dbScheme = impl.getDataSchemas
          var goToTable = getTableNameByElement(impl.getReverseTAssociate, element)
          var parent = getParent(impl.getSigs, element)
          if (parent == null) { // element is a root class
            var allAboutOMClass: ArrayList[CodeNamePair[String]] = dbScheme.get(goToTable)
            fromPart += "`" + element + "`"
            for (pair <- allAboutOMClass if pair.getFirst.equalsIgnoreCase("fields")) {
              var fieldName = pair.getSecond()
              selectPart += "`" + element + "`.`" + fieldName + "`,"
              if (isPrimaryKeys(impl.getPrimaryKeys, element, fieldName)) {
                val value = getFieldValue(allAboutOMClass, fieldName)
                wherePart += "`" + element + "`.`" + fieldName + "`=" + value + " AND "
              }
            }
          } else if (!goToTable.equalsIgnoreCase(element)) { // class C is mapped to the same table as its super class

          } else if (goToTable.equalsIgnoreCase(element)) { // class C is mapped to its own table
            fromPart += "`" + goToTable + "`";
            var allAboutOMClass: ArrayList[CodeNamePair[String]] = dbScheme.get(goToTable)
            for (pair <- allAboutOMClass if pair.getFirst().equalsIgnoreCase("fields")) {
              var fieldName = pair.getSecond()
              selectPart += "`" + element + "`.`" + fieldName + "`,";
              if (isPrimaryKeys(impl.getPrimaryKeys, element, fieldName)) {
                val value = getFieldValue(fieldValuePairs, fieldName)
                wherePart += "`" + element + "`.`" + fieldName + "`=" + value + " AND ";
              }
            }
          }
          selectPart = selectPart.substring(0, selectPart.length() - 1);
          wherePart = wherePart.substring(0, wherePart.length() - 5);
          var stmt = selectPart + fromPart + wherePart + ";";
          if (!stmt.substring(0, 11).equalsIgnoreCase("select from")) {
            stmt += "RESET QUERY CACHE;";
            addSelectStmtIntoDataSchema(allSelectStmts, goToTable, stmt);
          }
        }

      }
    }
    var sq = new SpecializedQuery()
    sq.setSelectStmtsInOneObject(allSelectStmts)
    sq
  }

  def addSelectStmtIntoDataSchema(allStmts: HashMap[String, ArrayList[String]],
    tableName: String, stmt: String) = {
    var contains: Boolean = allStmts.containsKey(tableName)
    if (!contains) {
      allStmts.put(tableName, new ArrayList[String]())
    }
    allStmts.get(tableName).add(stmt)
  }

  def genObjects(objSpec: ObjectSpec): ObjectSet = {
    /**
     * get the path of object specification
     * construct solution folder
     * prepare environment for alloy analyzer
     * call alloy analyzer
     * write solution into xml files
     * scan the solution folder and get the path of solutions
     */

    var specPath = objSpec.getSpecPath()
    var lenOfExtension = "_dm.als".length()
    var objectSolFolder = specPath.substring(0, specPath.length() - lenOfExtension)
    //    var specName = specPath.substring(specPath.lastIndexOf(File.separator) + 1, specPath.indexOf("_"))
    objectSolFolder = objectSolFolder + File.separator + "TestSolutions"
    if (!new File(objectSolFolder).exists()) {
      new File(objectSolFolder).mkdirs()
    }

    // call objects generator
    var loadSynthesizer = new LoadSynthesizer()
    loadSynthesizer.genObjects(specPath, objectSolFolder)

    // scan solution folder to get objects' path
    var objectFiles: Array[File] = new java.io.File(objectSolFolder).listFiles.filter(_.getName.endsWith(".xml"))
    var file: File = null
    var objects: ObjectSet = new ObjectSet()

    for (file <- objectFiles) {
      var objectFileName = file.getName()
      var singleObject = new ObjectOfDM(file.getAbsolutePath())
      objects.getObjSet().add(singleObject)
    }
    objects
  }

  def genObjSpec(fSpec: FormalSpecificationType): ObjectSpec = {
    println("This is myLFunction function")
    /**
     * construct object specification name from FormalSpecification
     * manually set intScopt as 6 (task)
     * new AlloyOMToAllotDM to get sigs
     * new ORMParser to get
     */
    var fSpecPath = fSpec.asInstanceOf[DBFormalSpecification].getSpec
    var objSpecPath = fSpecPath.substring(0, fSpecPath.length() - 4) + "_dm.als"
    var intScope = 6

    var aotad: AlloyOMToAlloyDM = new AlloyOMToAlloyDM()
    // by calling run, (legacy) Object Specification will be created
    aotad.run(fSpecPath, objSpecPath, intScope)

    var objSpec = new ObjectSpec()

    var dbDSpec = fSpec.asInstanceOf[DBFormalSpecification]
    objSpec.setIds(dbDSpec.getIds)
    objSpec.setAssociations(dbDSpec.getAssociations)
    objSpec.setTypeList(dbDSpec.getTypeMap())
    objSpec.setSigs(dbDSpec.getSigs())

    objSpec.setSpecPath(objSpecPath)
    objSpec
  }

  def myTFunction(fAB: FormalAbstractMeasurementFunctionSet): (List[ImplementationType] => List[FormalConcreteMeasurementFunctionSet]) = {
    def returnFunction(implList: List[ImplementationType]): List[FormalConcreteMeasurementFunctionSet] = {
      // convert between List in extracted code and ArrayList in Java
      var impls: ArrayList[DBImplementation] = new ArrayList[DBImplementation]()

      var defaultValue = Nil[ImplementationType]()
      var implHd: ImplementationType = hd[ImplementationType](defaultValue)(implList)
      var implTl = tl[ImplementationType](implList)

      while (implHd != defaultValue) {
        impls.add(implHd.asInstanceOf[DBImplementation])
        var tmp = hd[ImplementationType](defaultValue)(implTl)
        if (tmp != Nil[ImplementationType]()) {
          implHd = tmp.asInstanceOf[DBImplementation]
          implTl = tl[ImplementationType](implTl)
        }
        implHd = tmp;
      }

      var concreteMFSet: FormalConcreteMeasurementFunctionSet = getConcreteMeasurementFunctionSets(fAB.asInstanceOf[DBFormalAbstractMeasurementFunctionSet], impls)
      var fCMFS: DBFormalConcreteMeasurementFunctionSet = new DBFormalConcreteMeasurementFunctionSet()
      var fCMFSList: List[FormalConcreteMeasurementFunctionSet] = Nil[FormalConcreteMeasurementFunctionSet]()
      fCMFSList
    }
    returnFunction
  }

  def mySFunction(spec: SpecificationType): FormalSpecificationType = {
    println("This is mySFunction function")
    var dbfs = new DBFormalSpecification(spec.asInstanceOf[DBSpecification].getSpecFile)
    // parse spec file and fill in all members
    // Chong: check how to define constructor in Scala, and call parseSepc() in consctructor
    dbfs.parseSpec()
    dbfs
  }

  def myIFunctionHelper(fciList: List[FormalImplementationType]): List[ImplementationType] = {
    var implList: List[ImplementationType] = Nil[ImplementationType]()

    var defaultValue = new DBFormalImplementation()

    var tmp = hd[FormalImplementationType](defaultValue)(fciList)
    var tail = tl[FormalImplementationType](fciList)
    while (tmp != defaultValue) {
      var dbi: ImplementationType = myIFunction(tmp)
      implList = Cons[ImplementationType](dbi.asInstanceOf[DBImplementation], implList)
      tmp = hd[FormalImplementationType](defaultValue)(tail)
      tail = tl[FormalImplementationType](tail)
    }
    implList
  }

  def myIFunction(fImp: FormalImplementationType): ImplementationType = {
    println("This is myIFunction function")

    /**
     * compute FormalImplementation schema name
     * sigs here is all signatures in FormalSpecification (alloyOM), which already be set by lFunction
     * set all needed information for test cases generation here, initialize the global variable SolveAlloyDM
     */
    var fImpFileName = fImp.asInstanceOf[DBFormalImplementation].getImplementation
    var impFileName = fImpFileName.substring(0, fImpFileName.length() - 4) + ".sql"

    var parser = new ORMParser(fImpFileName, impFileName, fImp.asInstanceOf[DBFormalImplementation].getSigs)
    parser.createSchemas();
    /**
     * Need to set all needed information for test cases generation here
     */

    var dbFImpl: DBFormalImplementation = fImp.asInstanceOf[DBFormalImplementation]

    var impl = new DBImplementation(impFileName)
    impl.setAllFields(parser.getallFields())
    impl.setPrimaryKeys(parser.getPrimaryKeys())
    impl.setReverseTAssociate(parser.getReverseTAssociate())
    impl.setFields(parser.getFields())
    impl.setFieldsTable(parser.getFieldsTable())
    impl.setForeignKeys(parser.getForeignKey())
    impl.setDataProvider(parser.getDataProvider())
    impl.setAssociations(parser.getAssociations())

    impl.setSigs(dbFImpl.getSigs)
    impl.setIds(dbFImpl.getIds)
    impl.setAssociationsForCreateSchemas(dbFImpl.getAssociationsForCreateSchemas)
    impl.setTypeMap(dbFImpl.getTypeMap)

    impl
  }

  def myBFunctionHelper(fcbList: List[FormalConcreteMeasurementFunctionSet]): List[MeasurementFunctionSetType] = {
    // iterate whole list Concrete Measurement Function
    // define a default value to call hd()
    var mfSetList = Nil[MeasurementFunctionSetType]().asInstanceOf[List[MeasurementFunctionSetType]]
    var defaultValue = Nil[FormalConcreteMeasurementFunctionSet]()
    var fcfHead = hd[FormalConcreteMeasurementFunctionSet](defaultValue)(fcbList)
    var fcfTail = tl[FormalConcreteMeasurementFunctionSet](fcbList)

    while (fcfHead != defaultValue) {
      var result = myBFunction(fcfHead)
      mfSetList = new Cons[MeasurementFunctionSetType](result, mfSetList)
      fcfTail = tl[FormalConcreteMeasurementFunctionSet](fcfTail)
      fcfHead = hd[FormalConcreteMeasurementFunctionSet](defaultValue)(fcfTail)
    }
    mfSetList
  }

  // BFunction here is an identity function
  def myBFunction(fCB: FormalConcreteMeasurementFunctionSet): MeasurementFunctionSetType = {
    var castedfCB = fCB.asInstanceOf[DBFormalConcreteMeasurementFunctionSet]
    var tLoads = castedfCB.getCtmf().getLoads()
    var sLoads = castedfCB.getCsmf().getLoads()

    //    var mfSet =

    println("This is myBFunction function")
    new DBBenchmark()
  }

  var myTrademaker: Trademaker = new Build_Trademaker(
    myTradespace,
    myParetoFront,
    myCFunction,
    myAFunction,
    myLFunction,
    myTFunction,
    mySFunction,
    myIFunction,
    myBFunction)
}