package locales

import treehugger.forest._
import definitions._
import treehuggerDSL._
import locales.cldr._

object CodeGenerator {
  val autoGeneratedCommend = "Auto-generated code from CLDR definitions, don't edit"

  def buildClassTree(
      packageObject: String,
      ldmls: List[XMLLDML],
      only: List[String],
      parentLocales: Map[String, List[String]]
  ): Tree = {
    val langs = ldmls.map(_.scalaSafeName.split("_").toList)
    // Root must always be available
    val root = ldmls.find(_.scalaSafeName == "root").get

    val objectBlock = if (only.nonEmpty) {
      ldmls
        .filter(a => only.contains(a.scalaSafeName))
        .map(buildClassTree(root, langs, parentLocales))
    } else {
      ldmls.map(buildClassTree(root, langs, parentLocales))
    }

    BLOCK(
      List(
        IMPORT(REF("locales.cldr.LDML")).withComment(autoGeneratedCommend),
        IMPORT(REF("locales.cldr.LDMLLocale")),
        IMPORT(REF("locales.cldr.Symbols")),
        IMPORT(REF("locales.cldr.CalendarSymbols")),
        IMPORT(REF("locales.cldr.CalendarPatterns")),
        IMPORT(REF("locales.cldr.NumberPatterns")),
        IMPORT(REF("locales.cldr.NumberCurrency")),
        IMPORT(REF("locales.cldr.CurrencySymbol")),
        IMPORT(REF("locales.cldr.CurrencyDisplayName")),
        IMPORT(REF("locales.cldr.data.numericsystems._"))
      ) ++ objectBlock
    ).inPackage("locales.cldr.data")
  }

  def findParent(
      root: XMLLDML,
      langs: List[List[String]],
      ldml: XMLLDML,
      parentLocales: Map[String, List[String]]
  ): Option[String] =
    // http://www.unicode.org/reports/tr35/#Locale_Inheritance
    parentLocales
      .find(_._2.contains(ldml.fileName))
      .fold(
        // This searches based on the simple hierarchy resolution based on bundle_name
        // http://www.unicode.org/reports/tr35/#Bundle_vs_Item_Lookup
        ldml.scalaSafeName.split("_").reverse.toList match {
          case x :: Nil if x == root.scalaSafeName   => None
          case x :: Nil                              => Some(root.scalaSafeName)
          case x :: xs if langs.contains(xs.reverse) => Some(xs.reverse.mkString("_"))
        }
      )(p => Some(p._1))

  def buildClassTree(
      root: XMLLDML,
      langs: List[List[String]],
      parentLocales: Map[String, List[String]]
  )(ldml: XMLLDML): Tree = {
    val ldmlSym                 = getModule("LDML")
    val ldmlNumericSym          = getModule("Symbols")
    val ldmlNumberCurrency      = getModule("NumberCurrency")
    val ldmlCurrencySym         = getModule("CurrencySymbol")
    val ldmlCurrencyDisplayName = getModule("CurrencyDisplayName")
    val ldmlCalendarSym         = getModule("CalendarSymbols")
    val ldmlCalendarPatternsSym = getModule("CalendarPatterns")
    val ldmlNumberPatternsSym   = getModule("NumberPatterns")
    val ldmlLocaleSym           = getModule("LDMLLocale")

    val parent = findParent(root, langs, ldml, parentLocales).fold(NONE)(v => SOME(REF(v)))

    val ldmlLocaleTree = Apply(
      ldmlLocaleSym,
      LIT(ldml.locale.language),
      ldml.locale.territory.fold(NONE)(t => SOME(LIT(t))),
      ldml.locale.variant.fold(NONE)(v => SOME(LIT(v))),
      ldml.locale.script.fold(NONE)(s => SOME(LIT(s)))
    )

    val defaultNS = ldml.defaultNS.fold(NONE)(s => SOME(REF(s.id)))

    // Locales only use the default numeric system
    val numericSymbols = ldml.digitSymbols.map {
      case (ns, symb) =>
        val decimal  = symb.decimal.fold(NONE)(s => SOME(LIT(s)))
        val group    = symb.group.fold(NONE)(s => SOME(LIT(s)))
        val list     = symb.list.fold(NONE)(s => SOME(LIT(s)))
        val percent  = symb.percent.fold(NONE)(s => SOME(LIT(s)))
        val minus    = symb.minus.fold(NONE)(s => SOME(LIT(s)))
        val perMille = symb.perMille.fold(NONE)(s => SOME(LIT(s)))
        val infinity = symb.infinity.fold(NONE)(s => SOME(LIT(s)))
        val nan      = symb.nan.fold(NONE)(s => SOME(LIT(s)))
        val exp      = symb.exp.fold(NONE)(s => SOME(LIT(s)))
        Apply(
          ldmlNumericSym,
          REF(ns.id),
          symb.aliasOf.fold(NONE)(n => SOME(REF(n.id))),
          decimal,
          group,
          list,
          percent,
          minus,
          perMille,
          infinity,
          nan,
          exp
        )
    }

    val currencies = ldml.currencies.map { c =>
      val symbols = LIST(c.symbols.map { s =>
        Apply(ldmlCurrencySym, LIT(s.symbol), LITOPTION(s.alt))
      })

      val displayNames = LIST(c.displayNames.map { n =>
        Apply(ldmlCurrencyDisplayName, LIT(n.name), LITOPTION(n.count))
      })

      Apply(ldmlNumberCurrency, LIT(c.currencyCode), symbols, displayNames)
    }

    val gc = ldml.calendar
      .map { cs =>
        Apply(
          ldmlCalendarSym,
          LIST(cs.months.map(LIT(_))),
          LIST(cs.shortMonths.map(LIT(_))),
          LIST(cs.weekdays.map(LIT(_))),
          LIST(cs.shortWeekdays.map(LIT(_))),
          LIST(cs.amPm.map(LIT(_))),
          LIST(cs.eras.map(LIT(_)))
        )
      }
      .fold(NONE)(s => SOME(s))

    val gcp = ldml.datePatterns
      .map { cs =>
        def patternToIndex(i: String) = i match {
          case "full"   => 0
          case "long"   => 1
          case "medium" => 2
          case "short"  => 3
          case x        => throw new IllegalArgumentException(s"Unknown format $x, abort ")
        }

        val dates = MAKE_MAP(
          cs.datePatterns.map(p => TUPLE(LIT(patternToIndex(p.patternType)), LIT(p.pattern)))
        )
        val times = MAKE_MAP(
          cs.timePatterns.map(p => TUPLE(LIT(patternToIndex(p.patternType)), LIT(p.pattern)))
        )
        Apply(ldmlCalendarPatternsSym, dates, times)
      }
      .fold(NONE)(s => SOME(s))

    val np = {
      val decimal  = ldml.numberPatterns.decimalFormat.fold(NONE)(s => SOME(LIT(s)))
      val percent  = ldml.numberPatterns.percentFormat.fold(NONE)(s => SOME(LIT(s)))
      val currency = ldml.numberPatterns.currencyFormat.fold(NONE)(s => SOME(LIT(s)))
      Apply(ldmlNumberPatternsSym, decimal, percent, currency)
    }

    OBJECTDEF(ldml.scalaSafeName).withParents(
      Apply(
        ldmlSym,
        parent,
        ldmlLocaleTree,
        defaultNS,
        LIST(numericSymbols),
        gc,
        gcp,
        LIST(currencies),
        np
      )
    )
  }

  def metadata(codes: List[String], languages: List[String], scripts: List[String]): Tree =
    BLOCK(
      OBJECTDEF("metadata") := BLOCK(
        LAZYVAL("isoCountries", "Array[String]") :=
          ARRAY(codes.map(LIT(_))).withComment(autoGeneratedCommend),
        LAZYVAL("isoLanguages", "Array[String]") :=
          ARRAY(languages.map(LIT(_))).withComment(autoGeneratedCommend),
        LAZYVAL("scripts", "Array[String]") :=
          ARRAY(scripts.map(LIT(_))).withComment(autoGeneratedCommend)
      )
    ).inPackage("locales.cldr.data")

  def localesProvider(): Tree =
    BLOCK(
      IMPORT(REF("locales.cldr.LocalesProvider")).withComment(autoGeneratedCommend),
      OBJECTDEF("provider") := BLOCK(
        // LAZYVAL("provider", "LocalesProvider") :=
        //   ARRAY(codes.map(LIT(_))) withComment autoGeneratedCommend,
        // LAZYVAL("isoLanguages", "Array[String]") :=
        //   ARRAY(languages.map(LIT(_))) withComment autoGeneratedCommend,
        // LAZYVAL("scripts", "Array[String]") :=
        //   ARRAY(scripts.map(LIT(_))) withComment autoGeneratedCommend
      )
    ).inPackage("locales.cldr.data")

  def numericSystems(ns: List[NumberingSystem], nsFilter: String => Boolean): Tree = {
    val ldmlNS = getModule("NumberingSystem")

    BLOCK(
      IMPORT(REF("locales.cldr.NumberingSystem")).withComment(autoGeneratedCommend),
      OBJECTDEF("numericsystems") := BLOCK(
        ns.filter(n => nsFilter(n.id))
          .map(s =>
            LAZYVAL(s.id, "NumberingSystem") :=
              Apply(ldmlNS, LIT(s.id), LIST(s.digits.toList.map(LIT(_))))
          )
      )
    ).inPackage("locales.cldr.data")
  }

  def calendars(c: List[Calendar], filter: String => Boolean): Tree = {
    val ldmlNS    = getModule("Calendar")
    val calendars = c.filter(c => filter(c.id))

    BLOCK(
      IMPORT(REF("locales.cldr.Calendar")),
      IMPORT(REF("locales.cldr.data.model._")).withComment(autoGeneratedCommend),
      OBJECTDEF("calendars") := BLOCK(
        (LAZYVAL("all", "List[Calendar]") := LIST(calendars.map(c => REF(c.safeName)))) +:
          calendars
            .map(c =>
              LAZYVAL(c.safeName, "Calendar") :=
                Apply(ldmlNS, LIT(c.id))
            )
      )
    ).inPackage("locales.cldr.data")
  }

  // Take an Option("foo") and generate the SOME(LIT("FOO"))
  private def LITOPTION(o: Option[_]): Tree = o.fold(NONE)(v => SOME(LIT(v)))

  def currencyData(c: CurrencyData): Tree =
    BLOCK(
      IMPORT(REF("locales.cldr.CurrencyType")).withComment(autoGeneratedCommend),
      IMPORT(REF("locales.cldr.CurrencyDataFractionsInfo")),
      IMPORT(REF("locales.cldr.CurrencyDataRegion")),
      IMPORT(REF("locales.cldr.CurrencyDataRegionCurrency")),
      IMPORT(REF("locales.cldr.CurrencyNumericCode")),
      OBJECTDEF("currencydata") := BLOCK(
        VAL("currencyTypes", "List[CurrencyType]") := LIST(c.currencyTypes.map {
          code: CurrencyType =>
            REF("CurrencyType").APPLY(LIT(code.currencyCode), LIT(code.currencyName))
        }),
        VAL("fractions", "List[CurrencyDataFractionsInfo]") := LIST(c.fractions.map {
          info: CurrencyDataFractionsInfo =>
            REF("CurrencyDataFractionsInfo").APPLY(
              LIT(info.currencyCode),
              LIT(info.digits),
              LIT(info.rounding),
              LITOPTION(info.cashDigits),
              LITOPTION(info.cashRounding)
            )
        }),
        VAL("regions", "List[CurrencyDataRegion]") := LIST(c.regions.map {
          region: CurrencyDataRegion =>
            REF("CurrencyDataRegion").APPLY(
              LIT(region.countryCode),
              LIST(region.currencies.map { currency: CurrencyDataRegionCurrency =>
                REF("CurrencyDataRegionCurrency").APPLY(
                  LIT(currency.currencyCode),
                  LITOPTION(currency.from), //DATEOPTION(currency.from),
                  LITOPTION(currency.to),   // DATEOPTION(currency.to),
                  LITOPTION(currency.tender)
                )
              })
            )
        }),
        VAL("numericCodes", "List[CurrencyNumericCode]") := LIST(c.numericCodes.map {
          code: CurrencyNumericCode =>
            REF("CurrencyNumericCode").APPLY(LIT(code.currencyCode), LIT(code.numericCode))
        })
      )
    ).inPackage("locales.cldr.data")
}
