package io.kaitai.struct

import io.kaitai.struct.datatype.DataType.{KaitaiStreamType, UserTypeInstream}
import io.kaitai.struct.datatype.{Endianness, InheritedEndian}
import io.kaitai.struct.format._
import io.kaitai.struct.languages.GoCompiler

import scala.collection.mutable.ListBuffer

class GoClassCompiler(
  override val topClass: ClassSpec,
  config: RuntimeConfig
) extends ClassCompiler(topClass, config, GoCompiler) {

  override def compileClass(curClass: ClassSpec): Unit = {
    provider.nowClass = curClass

    val extraAttrs = ListBuffer[AttrSpec]()
    extraAttrs += AttrSpec(List(), IoIdentifier, KaitaiStreamType)
    extraAttrs += AttrSpec(List(), RootIdentifier, UserTypeInstream(topClassName, None))
    extraAttrs += AttrSpec(List(), ParentIdentifier, curClass.parentType)

    if (!curClass.doc.isEmpty)
      lang.classDoc(curClass.name, curClass.doc)

    // Basic struct declaration
    lang.classHeader(curClass.name)
    compileAttrDeclarations(curClass.seq ++ extraAttrs)
    curClass.instances.foreach { case (instName, instSpec) =>
      compileInstanceDeclaration(instName, instSpec)
    }
    lang.classFooter(curClass.name)

    // Constructor
    compileConstructor(curClass)

    // Read method(s)
    compileEagerRead(curClass.seq, extraAttrs, curClass.meta.endian)

    compileInstances(curClass, extraAttrs)

    compileAttrReaders(curClass.seq ++ extraAttrs)

    compileEnums(curClass)

    // Recursive types
    compileSubclasses(curClass)
  }

  override def compileInstance(className: List[String], instName: InstanceIdentifier, instSpec: InstanceSpec, extraAttrs: ListBuffer[AttrSpec], endian: Option[Endianness]): Unit = {
    // FIXME: support calculated endianness

    // Determine datatype
    val dataType = instSpec.dataTypeComposite

    if (!instSpec.doc.isEmpty)
      lang.attributeDoc(instName, instSpec.doc)
    lang.instanceHeader(className, instName, dataType, instSpec.isNullable)
    lang.instanceCheckCacheAndReturn(instName)

    instSpec match {
      case vi: ValueInstanceSpec =>
        lang.attrParseIfHeader(instName, vi.ifExpr)
        lang.instanceCalculate(instName, dataType, vi.value)
        lang.attrParseIfFooter(vi.ifExpr)
      case i: ParseInstanceSpec =>
        lang.attrParse(i, instName, extraAttrs, None) // FIXME
    }

    lang.instanceSetCalculated(instName)
    lang.instanceReturn(instName)
    lang.instanceFooter
  }
}
