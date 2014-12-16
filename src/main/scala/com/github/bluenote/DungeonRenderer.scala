package com.github.bluenote

import org.lwjgl.opengl.GL11



case class Dungeon(floor: PolygonSet)

class Vbos(shader: Shader) {
  var visibleFloor: Option[StaticVbo] = None
  
  val player = new StaticVbo(VertexDataGen3D_NC.cylinder(0.5f, 1.8f, Color.COLOR_AMAZON, 32, false).transform(Mat4f.rotate(90, 1, 0, 0).translate(0, 0.9f, 0)), shader)
}


sealed trait DungeonEntity {
  val pos: Point
}

object DungeonEntity {
  case class Player(pos: Point) extends DungeonEntity
}



class DungeonRenderer(gameState: GameState) {

  // create vertex data + shader + VBO
  /*
  val vertexData = DungeonRenderer.generateVertexData(gameState.dungeon)
  val shader = new DefaultLightingShader()
  
  val vbo = new StaticVbo(vertexData, shader)
  */

  val shader = new DefaultLightingShader()

  val vbos = new Vbos(shader)
  
  //val entities = collection.mutable.HashSet[DungeonEntity]()
  
  var playerPos = gameState.playerPosition
  
  def update(change: GameStateChange) {
    change match {
      case GameStateChange.DungeonLoaded(dungeon) =>
        val vertexData = DungeonRenderer.generateVertexData(gameState.dungeon)
        vbos.visibleFloor = Some(new StaticVbo(vertexData, shader))
      case GameStateChange.PlayerPosition(pos) =>
        playerPos = pos
    }
  }
  
  
  
  def render(P: Mat4f, V: Mat4f) {
    GlWrapper.clearColor.set(Color(1f, 1f, 1f, 1f))
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT)
    
    GlWrapper.checkGlError("render -- before rendering the VBO")
    shader.use()
    shader.setProjection(P)
    shader.setModelview(V)

    vbos.visibleFloor.map(_.render())
    
    shader.setModelview(V.translate(playerPos.x, playerPos.y, 0))
    vbos.player.render()
    
    GlWrapper.checkGlError("render -- finished")
    }
  
}


object DungeonRenderer {
  
  def generateVertexData(dungeon: Dungeon): VertexData = {
    
    val floorTriangulated = dungeon.floor.triangulate()
    
    VertexDataGen3D_NC.fromTriangles(floorTriangulated, Color.COLOR_DARK_GRAY_X11, Vec3f(0,0,1))
  }

  def generateWalls(dungeon: Dungeon): VertexData = {
    val shells = dungeon.floor.shellPolygons
    
    for (shell <- shells) {
      
    }
    
    ???
  }
  
}


