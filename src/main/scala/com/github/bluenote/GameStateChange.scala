package com.github.bluenote

sealed trait GameStateChange

object GameStateChange {

  case class DungeonLoaded(dungeon: Dungeon) extends GameStateChange
  
  case class PlayerPosition(pos: Point) extends GameStateChange
  
}