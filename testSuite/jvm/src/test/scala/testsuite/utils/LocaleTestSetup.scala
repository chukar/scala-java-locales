package testsuite.utils

import java.util.Locale
import utest._

trait LocaleTestSetup {
  def assertEquals(msg: String, a: Any, b: Any) = assert(a == b)
  def assertEquals(a:   Any, b: Any) = assert(a == b)
  def assertTrue(a:     Boolean) = assert(a)
  def assertTrue(msg:   String, a: Boolean) = assert(a)
  def assertFalse(a:    Boolean) = assert(!a)
  def assertFalse(msg:  String, a: Boolean) = assert(!a)
  def assertNotSame(a:  AnyRef, b: AnyRef) = assert(!(a eq b))
  def assertSame(a:     AnyRef, b: AnyRef) = assert(a eq b)
  def assertNull(a:     Any) = assert(a == null)
  def fail() = assert(false)
  def assertArrayEquals(a: Array[AnyRef], b: Array[AnyRef]) = java.util.Arrays.equals(a, b)

  def cleanDatabase: Unit =
    Locale.setDefault(Locale.ENGLISH)
}
