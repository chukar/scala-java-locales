package locales

// Selection of Numbering Systems
sealed trait NumberingSystemFilter extends Product with Serializable {
  def filter: String => Boolean
}

object NumberingSystemFilter {
  case object None extends NumberingSystemFilter {
    def filter: String => Boolean = _ => false
  }
  case object All extends NumberingSystemFilter {
    def filter: String => Boolean = _ => true
  }
  final case class Selection(s: List[String]) extends NumberingSystemFilter {
    def filter: String => Boolean = s.contains
  }

  object Selection {
    def apply(s: String): Selection = Selection(List(s))
  }

}
