package com.github.bluenote

import scala.util.Random
import scala.collection.JavaConversions._
import java.util.ArrayList
import com.vividsolutions.jts.geom.{ Polygon => JtsPolygon }
import com.vividsolutions.jts.geom.{ MultiPolygon => JtsMultiPolygon }
import com.vividsolutions.jts.geom.{ Coordinate => JtsCoordinate }
import com.vividsolutions.jts.geom.{ LinearRing => JtsLinearRing }
import com.vividsolutions.jts.geom.GeometryFactory
import com.vividsolutions.jts.operation.union.CascadedPolygonUnion
import scala.collection.mutable.ArrayBuffer

/*
import uk.co.geolib.geopolygons.C2DPolygon
import uk.co.geolib.geolib.C2DPointSet
import uk.co.geolib.geolib.C2DPoint
import uk.co.geolib.geopolygons.C2DHoledPolyBaseSet

case class Point(x: Float, y: Float) {
  def toGeoPoint(): C2DPoint = new C2DPoint(x, y)
}

case class Polygon(points: Array[Point]) {

  def toGeoPolygon(): C2DPolygon = {
    val pset = new ArrayList(asJavaCollection(points.map(_.toGeoPoint)))
    val p = new C2DPolygon(pset, true)
    p
  }
  
}

object Polygon {
  def createUnion(polygons: Array[Polygon]) {
    val geoPolygons = polygons.map(_.toGeoPolygon)
    val polyset = new C2DHoledPolyBaseSet()
    //geoPolygons.foreach(p => polyset.AddAndUnify(p))
  }
  
}

case class PolygonWithHoles(points: Array[Point], holes: Array[Polygon]) {
  
}
*/

object JtsFactory {
  val factory = new GeometryFactory()
  def apply() = factory
}


case class Point(x: Float, y: Float) {
  def toJtsCoordinate() = new JtsCoordinate(x, y)
}



case class Polygon(points: Array[Point]) {

  def toJtsPolygon(): JtsPolygon = {
    val pointsWithRepeatedStart = points :+ points(0)
    val linearRing = JtsFactory().createLinearRing(pointsWithRepeatedStart.map(_.toJtsCoordinate))
    val holes: Array[JtsLinearRing] = Array()
    new JtsPolygon(linearRing, holes, JtsFactory())
  }
  
}

object Polygon {
  def createUnion(polygons: Array[Polygon]): Array[Polygon] = {
    val cascade = new CascadedPolygonUnion(polygons.map(_.toJtsPolygon).to)
    val union = cascade.union()
    val gtype = union.getGeometryType()
    println(gtype)
    val outPolys = ArrayBuffer[Polygon]()
    for (i <- Range(0, union.getNumGeometries)) {
      val geom = union.getGeometryN(i)
      val gtype = geom.getGeometryType()
      println(gtype)
      geom match {
        case poly: JtsPolygon =>
          val coords = poly.getExteriorRing().getCoordinates()
          val points = coords.dropRight(1).map(coord => Point(coord.getOrdinate(0).toFloat, coord.getOrdinate(1).toFloat))
          outPolys += Polygon(points)
        case _ => {}
      }
    }
    /*
    if (union.isInstanceOf[JtsMultiPolygon]) {
      println("yes")
      val mpoly = union.asInstanceOf[JtsMultiPolygon]
      mpoly.
    }
    */
    outPolys.toArray
  }
  
}


case class PolygonWithHoles(points: Array[Point], holes: Array[Polygon]) {
  
}


case class Rect(x1: Float, x2: Float, y1: Float, y2: Float) {
  def toPolygon(): Polygon = Polygon(Array(Point(x1,y1),
                                           Point(x1,y2),
                                           Point(x2,y2),
                                           Point(x2,y1)))
}

object Rect {
  def createFromCenter(x: Float, y: Float, w: Float, h: Float) =
    Rect(x-w/2, x+w/2, y-h/2, y+h/2)
}




object DungeonGenerator {

  def generate() {
    
    val numRooms = 20 // Random.nextInt()
    
    val rooms = Array.tabulate(numRooms){ i =>
      Rect.createFromCenter(Random.nextFloat*100, Random.nextFloat*100, Random.nextFloat*20f + 5, Random.nextFloat*20f + 5)
      //va
    }
    
    val polys = rooms.map(_.toPolygon)
    
    val polysUnified = Polygon.createUnion(polys)
    
    ImageGenerator.writePolygons("polygons", polys)
    ImageGenerator.writePolygons("polygonsUnified", polysUnified)
  }
  
  def main(args: Array[String]) {
    generate()
  }
}
















