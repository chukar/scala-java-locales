package locales

import java.text.DecimalFormatSymbols
import java.util.Locale

import scala.collection.{Map, mutable}
import locales.cldr.LDML
import locales.cldr.data

/**
  * Implements a database of locales
  */
object LocaleRegistry {

  // The spec requires some locales by default
  // lazy val en: LDML         = ldml(Locale.ENGLISH).getOrElse(root)
  lazy val root: LDML       = data.root

  case class LocaleCldr(locale: Locale,
      decimalFormatSymbol: Option[DecimalFormatSymbols])

  private var defaultLocale: Locale = data.root.toLocale
  private var defaultPerCategory: Map[Locale.Category, Option[Locale]] =
    Locale.Category.values().map(_ -> Some(defaultLocale)).toMap

  private lazy val ldmls: mutable.Map[String, LDML] = mutable.Map.empty

  initDefaultLocales()

  /**
    * Install an ldml class making its locale available to the runtime
    */
  def installLocale(ldml: LDML): Unit = ldmls += ldml.languageTag -> ldml

  /**
    * Cleans the registry, useful for testing
    */
  def resetRegistry(): Unit = {
    defaultLocale = data.root.toLocale
    defaultPerCategory =
        Locale.Category.values().map(_ -> Some(defaultLocale)).toMap
    ldmls.clear()
    initDefaultLocales()
  }

  private def initDefaultLocales(): Unit = {
    // Initialize defaults
    // defaultLocales.foreach {
    //   case (_, l) => installLocale(l)
    // }
  }

  def default: Locale = defaultLocale

  def default(category: Locale.Category): Locale = {
    if (category == null) {
      throw new NullPointerException("Argument cannot be null")
    } else {
      defaultPerCategory.get(category).flatten
        .getOrElse(throw new IllegalStateException(s"No default locale set for category $category"))
    }
  }

  def setDefault(newLocale: Locale): Unit = {
    if (newLocale == null) {
      throw new NullPointerException("Argument cannot be null")
    }
    defaultLocale = newLocale
    defaultPerCategory = Locale.Category.values().map(_ -> Some(newLocale)).toMap
  }

  def setDefault(category: Locale.Category, newLocale: Locale): Unit = {
    if (category == null || newLocale == null) {
      throw new NullPointerException("Argument cannot be null")
    } else {
      defaultPerCategory = defaultPerCategory + (category -> Some(newLocale))
    }
  }

  /**
    * Attempts to give a Locale for the given tag if available
    */
  def localeForLanguageTag(languageTag: String): Option[Locale] = {
    // TODO Support alternative tags for the same locale
    ldmls.get(languageTag).map(_.toLocale)
  }

  /**
    * Returns a list of available locales
    */
  def availableLocales: Iterable[Locale] = ldmls.map(_._2.toLocale)

  /**
    * Returns the ldml for the given locale
    */
  def ldml(locale: Locale): Option[LDML] = ldmls.get(locale.toLanguageTag)
}
