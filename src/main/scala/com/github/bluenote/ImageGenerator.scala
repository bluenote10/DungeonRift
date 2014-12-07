package com.github.bluenote

import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.GeneralPath
import java.awt.Graphics2D
import java.awt.GradientPaint
import java.awt.BasicStroke
import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.{ Polygon => AwtPolygon }
import java.awt.{ Point => AwtPoint }
import java.awt.{ Color => AwtColor }
import org.apache.batik.svggen.SVGGraphics2D
import org.apache.batik.dom.GenericDOMImplementation
import org.w3c.dom.Document
import org.w3c.dom.DOMImplementation
import java.io.OutputStreamWriter
import java.io.FileOutputStream
import java.awt.geom.Path2D


case class LinearTransformation(min: Float, max: Float, intendedMin: Float = 0, intendedMax: Float = 1000) {
  val w = max - min
  val intendedW = intendedMax - intendedMin
  def apply(x: Float): Float = {
    val s = (x-min) / w
    intendedMin + s*intendedW
  }
}


object ImageGenerator {

  def storeImage(img: BufferedImage, filename: String) {
    val outputfile = new File(filename + ".png")
    outputfile.getParentFile().mkdirs()
    outputfile.createNewFile()
    ImageIO.write(img, "png", outputfile)
  }
  
  def generateImageFromLabelString(text: String, font: Font, w: Int, h: Int): BufferedImage = {
    val fontScaled = font.deriveFont(h.toFloat/2) 
    val img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g2d = img.createGraphics()
    g2d.setPaint(java.awt.Color.white)
    g2d.setFont(fontScaled)
    val fm = g2d.getFontMetrics()
    val x = img.getWidth()/2 - fm.stringWidth(text)/2
    val y = (img.getHeight()*0.7f).toInt  // /2 + fm.getAscent()/2              // y axis goes DOWN in g2d
    g2d.drawString(text, x, y)
    g2d.dispose()
    img
  }
  
  /**
   * It looks like it is not possible to clip a Path2D, so this simple draws 
   * the shells of all PolygonWithHoles and ignores the holes. 
   */
  def writePolygons(filename: String, polygonSet: PolygonSet) {
    writePolygons(filename, polygonSet.shellPolygons)
  }
  
  def writePolygons(filename: String, polygons: Array[Polygon]) {
    val minX = polygons.map(_.points.map(_.x).min).min
    val maxX = polygons.map(_.points.map(_.x).max).max
    val minY = polygons.map(_.points.map(_.y).min).min
    val maxY = polygons.map(_.points.map(_.y).max).max
    val xTrans = LinearTransformation(minX, maxX)
    val yTrans = LinearTransformation(minY, maxY)
    writeSVG(filename){ g2d =>
      for (poly <- polygons) {
        val path = new Path2D.Float
        val points = poly.points
        path.moveTo(xTrans(points(0).x), yTrans(points(0).y))
        for (p <- points) {
          path.lineTo(xTrans(p.x), yTrans(p.y))
        }
        path.lineTo(xTrans(points(0).x), yTrans(points(0).y))
        g2d.setPaint(java.awt.Color.BLACK)
        g2d.draw(path)
        g2d.setPaint(new AwtColor(10, 20, 200, 100))
        g2d.fill(path)
      }
    }
  }
  
  /**
   * This follows the example from:
   *   http://xmlgraphics.apache.org/batik/using/svg-generator.html
   */
  def writeSVG(filename: String)(painter: Graphics2D => Unit) {
    val domImpl = GenericDOMImplementation.getDOMImplementation
    val svgNS = "http://www.w3.org/2000/svg"
    val document = domImpl.createDocument(svgNS, "svg", null)
    
    val svgGenerator = new SVGGraphics2D(document)
    
    painter(svgGenerator)

    val outputfile = new File(filename + ".svg")
    //outputfile.getParentFile().mkdirs()

    val useCSS = true
    val fs = new FileOutputStream(outputfile)
    val out = new OutputStreamWriter(fs, "UTF-8")
    svgGenerator.stream(out, useCSS)
  }
  
}