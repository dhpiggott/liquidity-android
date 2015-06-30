package com.dhpcs.liquidity

sealed trait GameType extends Serializable {

  def name: String

}

case object MONOPOLY extends GameType {

  def name = "monopoly"

}

case object TEST extends GameType {

  def name = "test"

}
