package testsuite.javalib.util

import java.util.Locale
import java.util.Currency
import testsuite.utils.AssertThrows.expectThrows

class JSCurrencyTest extends munit.FunSuite with CurrencyTest {

  test("available_currencies") {
    assert(Currency.getAvailableCurrencies().size() > 0)
  }

  test("standard_locales") {
    test_standard_locales(_.jsResults)
  }
}
