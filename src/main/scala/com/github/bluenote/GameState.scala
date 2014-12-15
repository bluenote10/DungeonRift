package com.github.bluenote




/*
class GameState {

  val dungeon: Dungeon = DungeonGenerator.generate
  
  val playerPosition = Point(dungeon.)
}
*/

case class GameState(
  var dungeon: Dungeon, 
  var playerPosition: Point
) {
  
  def move(dir: String) {
    dir match {
      case "u" => playerPosition = playerPosition.map(p => Point(p.x, p.y+1))
      case "d" => playerPosition = playerPosition.map(p => Point(p.x, p.y-1))
      case "l" => playerPosition = playerPosition.map(p => Point(p.x-1, p.y))
      case "r" => playerPosition = playerPosition.map(p => Point(p.x+1, p.y))
    }
  }
  
}

object GameState {
  
  def initialize(): GameState = {
    
    val dungeon = DungeonGenerator.generate
    
    val dungeonRect = dungeon.floor.boundingBox
    val playerPosition = dungeonRect.center
    
    GameState(dungeon, playerPosition)
  }
  
}





