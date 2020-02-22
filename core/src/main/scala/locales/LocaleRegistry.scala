package locales

import java.text.DecimalFormatSymbols
import java.util.Locale

import scala.collection.{ Map, mutable }
import org.portablescala.reflect._
import locales.cldr.LDML
import locales.cldr.CLDRMetadata
import locales.cldr.LocalesProvider
import locales.cldr.NumberingSystem

/**
  * Implements a database of locales
  */
object LocaleRegistry {

  val provider: LocalesProvider =
    Reflect
      .lookupLoadableModuleClass("locales.cldr.data.LocalesProvider$", null)
      .getOrElse(sys.error("Needs a locale provider"))
      .loadModule
      .asInstanceOf[LocalesProvider]

  val metadata: CLDRMetadata = provider.metadata

  val ldmls = provider.ldmls

  // The spec requires some locales by default
  // lazy val en: LDML         = ldml(Locale.ENGLISH).getOrElse(root)
  lazy val root: LDML       = provider.root
  val latn: NumberingSystem = provider.latn
  val currencydata          = provider.currencyData

  case class LocaleCldr(locale: Locale, decimalFormatSymbol: Option[DecimalFormatSymbols])

  private var defaultLocale: Locale = root.toLocale
  private var defaultPerCategory: Map[Locale.Category, Option[Locale]] =
    Locale.Category.values().map(_ -> Some(defaultLocale)).toMap

  def default: Locale = defaultLocale

  def default(category: Locale.Category): Locale =
    if (category == null) {
      throw new NullPointerException("Argument cannot be null")
    } else {
      defaultPerCategory
        .get(category)
        .flatten
        .getOrElse(throw new IllegalStateException(s"No default locale set for category $category"))
    }

  def setDefault(newLocale: Locale): Unit = {
    if (newLocale == null) {
      throw new NullPointerException("Argument cannot be null")
    }
    defaultLocale      = newLocale
    defaultPerCategory = Locale.Category.values().map(_ -> Some(newLocale)).toMap
  }

  def setDefault(category: Locale.Category, newLocale: Locale): Unit =
    if (category == null || newLocale == null) {
      throw new NullPointerException("Argument cannot be null")
    } else {
      defaultPerCategory = defaultPerCategory + (category -> Some(newLocale))
    }

  /**
    * Attempts to give a Locale for the given tag if available
    */
  def localeForLanguageTag(languageTag: String): Option[Locale] =
    // TODO Support alternative tags for the same locale
    if (languageTag == "und") {
      Some(Locale.ROOT)
    } else
      provider.ldmls.get(languageTag).map(_.toLocale)

  /**
    * Returns a list of available locales
    */
  def availableLocales: Iterable[Locale] = provider.ldmls.map(_._2.toLocale)

  /**
    * Returns the ldml for the given locale
    */
  def ldml(locale: Locale): Option[LDML] = {
    val tag =
      if (locale.toLanguageTag() == "zh-CN") "zh-Hans-CN"
      else if (locale.toLanguageTag() == "zh-TW") "zh-Hant-TW"
      else locale.toLanguageTag()
    provider.ldmls.get(tag)
  }
}
