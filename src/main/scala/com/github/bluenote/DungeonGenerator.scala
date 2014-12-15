package com.github.bluenote

import scala.util.Random
import scala.collection.JavaConversions._
import java.util.ArrayList
import com.vividsolutions.jts.geom.{ Polygon => JtsPolygon }
import com.vividsolutions.jts.geom.{ MultiPolygon => JtsMultiPolygon }
import com.vividsolutions.jts.geom.{ Coordinate => JtsCoordinate }
import com.vividsolutions.jts.geom.{ LinearRing => JtsLinearRing }
import com.vividsolutions.jts.geom.{ LineString => JtsLineString }
import com.vividsolutions.jts.geom.GeometryFactory
import com.vividsolutions.jts.operation.union.CascadedPolygonUnion
import scala.collection.mutable.ArrayBuffer
import org.poly2tri.geometry.polygon.{ Polygon => P2TPolygon }
import org.poly2tri.geometry.polygon.{ PolygonPoint => P2TPoint }
import org.poly2tri.triangulation.TriangulationProcess
import org.poly2tri.triangulation.TriangulationAlgorithm
import org.poly2tri.Poly2Tri

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
  
  def *(scalar: Float) = Point(x*scalar, y*scalar)
  def /(scalar: Float) = Point(x/scalar, y/scalar)
  def +(scalar: Float) = Point(x+scalar, y+scalar)
  def -(scalar: Float) = Point(x-scalar, y-scalar)
  
  def map(mod: Point => Point) = mod(this)
  
  def toJtsCoordinate() = new JtsCoordinate(x, y)
  def toP2TPoint() = new P2TPoint(x, y)
}



case class Polygon(points: Array[Point]) {

  def boundingBox(): Rect = {
    Rect(
      points.map(_.x).min,
      points.map(_.x).max,
      points.map(_.y).min,
      points.map(_.y).max
    )
  }
  
  def toJtsLinearRing(): JtsLinearRing = {
    val pointsWithRepeatedStart = points :+ points(0)
    val pointsWithRepeatedStartConv = pointsWithRepeatedStart.map(_.toJtsCoordinate)
    val linearRing = JtsFactory().createLinearRing(pointsWithRepeatedStartConv)
    linearRing
  }
  
  def toJtsPolygon(): JtsPolygon = {
    val jtsShell = toJtsLinearRing()
    val jtsHoles = Array[JtsLinearRing]()
    val p = new JtsPolygon(jtsShell, jtsHoles, JtsFactory())
    p
  }
  
  def toP2TPolygon(): P2TPolygon = {
    val pointsConv = points.map(_.toP2TPoint)
    val p = new P2TPolygon(pointsConv)
    p
  }
  
  def triangulate() {
    val p2tPoly = this.toP2TPolygon
    //p2tPoly.prepareTriangulation(x$1)
  }
}





case class PolygonWithHoles(shell: Polygon, holes: Array[Polygon]) {

  def boundingBox(): Rect = {
    shell.boundingBox
  }
  
  def toJtsPolygon(): JtsPolygon = {
    val jtsShell = shell.toJtsLinearRing
    val jtsHoles = holes.map(_.toJtsLinearRing)
    val p = new JtsPolygon(jtsShell, jtsHoles, JtsFactory())
    p
  }
  
  def toP2TPolygon(): P2TPolygon = {
    val pointsConv = shell.points.map(_.toP2TPoint)
    val p = new P2TPolygon(pointsConv)
    for (hole <- holes) {
      p.addHole(hole.toP2TPolygon)
    }
    p
  }
  
  def triangulate(): Array[Triangle] = {
    //val process = new TriangulationProcess(TriangulationAlgorithm.DTSweep)
    
    val p2tPoly = this.toP2TPolygon
    //process.triangulate(p2tPoly)
    
    //process.
    Poly2Tri.triangulate(p2tPoly)
    //p2tPoly.prepareTriangulation(x$1)
    
    val triangles = p2tPoly.getTriangles().map { tri =>
      Triangle(
        tri.points(0).getXf(), tri.points(0).getYf(),
        tri.points(1).getXf(), tri.points(1).getYf(),
        tri.points(2).getXf(), tri.points(2).getYf()
      )
    }
    
    triangles.toArray
  }  
}


case class PolygonSet(polygons: Array[PolygonWithHoles]) {
  def shellPolygons() = polygons.map(_.shell)

  def boundingBox(): Rect = {
    val boxes = polygons.map(_.boundingBox)
    val minX = boxes.map(_.x1).min
    val maxX = boxes.map(_.x2).max
    val minY = boxes.map(_.y1).min
    val maxY = boxes.map(_.y2).max
    Rect(minX, maxX, minY, maxY)
  }
  
  def triangulate(): Array[Triangle] = {
    polygons.map(_.triangulate).flatten 
  }
}



object GeomSetOperations {
  
  def createUnion(polygons: Array[Polygon]): PolygonSet = {

    val outPolys = ArrayBuffer[PolygonWithHoles]()

    val cascade = new CascadedPolygonUnion(polygons.map(_.toJtsPolygon).to)
    val union = cascade.union()
    val gtype = union.getGeometryType()
    
    def convertRingToPolygon(ring: JtsLineString): Polygon = {
      val coords = ring.getCoordinates()
      val points = coords.dropRight(1).map(coord => Point(coord.getOrdinate(0).toFloat, coord.getOrdinate(1).toFloat))
      Polygon(points)
    }
    
    for (i <- Range(0, union.getNumGeometries)) {
      val geom = union.getGeometryN(i)
      val gtype = geom.getGeometryType()
      println(gtype)
      geom match {
        case poly: JtsPolygon =>
          val extRing = poly.getExteriorRing()
          val extPoly = convertRingToPolygon(extRing)
          val intPolys = Array.tabulate(poly.getNumInteriorRing) { h =>
            val intRing = poly.getInteriorRingN(h)
            val intPoly = convertRingToPolygon(intRing)
            intPoly
          }
          outPolys += PolygonWithHoles(extPoly, intPolys)
        case _ => {}
      }
    }
    /*
    if (union.isInstanceOf[JtsMultiPolygon]) {
      val mpoly = union.asInstanceOf[JtsMultiPolygon]
    }
    */
    PolygonSet(outPolys.toArray)
  }
  
}



// -------------------------------------------------------------------------------------------
// Geometric primitives
// -------------------------------------------------------------------------------------------

case class Rect(x1: Float, x2: Float, y1: Float, y2: Float) {
  def toPolygon(): Polygon = Polygon(Array(
    Point(x1,y1),
    Point(x1,y2),
    Point(x2,y2),
    Point(x2,y1)
  ))
  
  def center(): Point = Point((x1+x2)/2, (y1+y2)/2)
}

object Rect {
  def createFromCenter(x: Float, y: Float, w: Float, h: Float) =
    Rect(x-w/2, x+w/2, y-h/2, y+h/2)
}

case class Triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
  def toPolygon(): Polygon = Polygon(Array(
    Point(x1,y1),
    Point(x2,y2),
    Point(x3,y3)
  ))
}

object Triangle {
  // maybe constructors that ensure specific vertex winding?
}




object DungeonGenerator {

  def generate(): Dungeon = {
    
    val numRooms = 20 // Random.nextInt()
    
    val rooms = Array.tabulate(numRooms){ i =>
      Rect.createFromCenter(Random.nextFloat*100, Random.nextFloat*100, Random.nextFloat*20f + 5, Random.nextFloat*20f + 5)
      //va
    }
    
    val polys = rooms.map(_.toPolygon)
    
    val polysUnified = GeomSetOperations.createUnion(polys)
    
    ImageGenerator.writePolygons("polygons", polys)
    ImageGenerator.writePolygons("polygonsUnified", polysUnified)
    
    val triangles = polysUnified.triangulate 
    
    ImageGenerator.writePolygons("polygonsTriangulated", triangles.map(_.toPolygon))
    
    Dungeon(polysUnified)
  }
  
  def main(args: Array[String]) {
    generate()
  }
}
















