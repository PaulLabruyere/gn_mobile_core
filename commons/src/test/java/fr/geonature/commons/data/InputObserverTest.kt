package fr.geonature.commons.data

import android.database.Cursor
import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests about [InputObserver].
 *
 * @author [S. Grimault](mailto:sebastien.grimault@gmail.com)
 */
@RunWith(RobolectricTestRunner::class)
class InputObserverTest {

    @Test
    fun testEquals() {
        assertEquals(
            InputObserver(1234, "lastname", "firstname"),
            InputObserver(1234, "lastname", "firstname")
        )
    }

    @Test
    fun testCreateFromCursor() {
        // given a mocked Cursor
        val cursor = mock(Cursor::class.java)
        `when`(cursor.getColumnIndexOrThrow(InputObserver.COLUMN_ID)).thenReturn(0)
        `when`(cursor.getColumnIndexOrThrow(InputObserver.COLUMN_LASTNAME)).thenReturn(1)
        `when`(cursor.getColumnIndexOrThrow(InputObserver.COLUMN_FIRSTNAME)).thenReturn(2)
        `when`(cursor.getLong(0)).thenReturn(1234)
        `when`(cursor.getString(1)).thenReturn("lastname")
        `when`(cursor.getString(2)).thenReturn("firstname")

        // when getting InputObserver instance from Cursor
        val inputObserver = InputObserver.fromCursor(cursor)

        // then
        assertNotNull(inputObserver)
        assertEquals(InputObserver(1234, "lastname", "firstname"), inputObserver)
    }

    @Test
    fun testParcelable() {
        // given InputObserver
        val inputObserver = InputObserver(1234, "lastname", "firstname")

        // when we obtain a Parcel object to write the InputObserver instance to it
        val parcel = Parcel.obtain()
        inputObserver.writeToParcel(parcel, 0)

        // reset the parcel for reading
        parcel.setDataPosition(0)

        // then
        assertEquals(inputObserver, InputObserver.CREATOR.createFromParcel(parcel))
    }
}