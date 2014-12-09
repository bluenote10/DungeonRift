package com.github.bluenote

import org.lwjgl.opengl.GL11



case class Dungeon(floor: PolygonSet)



class DungeonRenderer(dungeon: Dungeon) {

  // create vertex data + shader + VBO
  val vertexData = DungeonRenderer.generateVertexData(dungeon)
  val shader = new DefaultLightingShader()
  
  val vbo = new StaticVbo(vertexData, shader)
  
  
  def render(P: Mat4f, V: Mat4f) {
    GlWrapper.clearColor.set(Color(1f, 1f, 1f, 1f))
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT)
    
    GlWrapper.checkGlError("render -- before rendering the VBO")
    shader.use()
    shader.setProjection(P)
    shader.setModelview(V)
    vbo.render()
    
    GlWrapper.checkGlError("render -- finished")
    }
  
}


object DungeonRenderer {
  
  def generateVertexData(dungeon: Dungeon): VertexData = {
    
    val floorTriangulated = dungeon.floor.triangulate()
    
    VertexDataGen3D_NC.fromTriangles(floorTriangulated, Color.COLOR_DARK_GRAY_X11, Vec3f(0,0,1))
  }
  
}


