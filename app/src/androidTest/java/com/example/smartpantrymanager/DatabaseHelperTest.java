package com.example.smartpantrymanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DatabaseHelperTest {

    private DatabaseHelper databaseHelper;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("SmartPantryTest.db");
        databaseHelper = new DatabaseHelper(context) {
            @Override
            public String getDatabaseName() {
                return "SmartPantryTest.db";
            }
        };
    }

    @After
    public void tearDown() {
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("SmartPantryTest.db");
    }

    @Test
    public void testDatabaseCreationAndDefaultRecipes() {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        assertTrue(db.isOpen());

        Cursor cursor = db.query("recipes", null, null, null, null, null, null);
        assertNotNull(cursor);
        assertTrue("Database should contain default recipes", cursor.getCount() > 0);
        assertEquals("Should have 15 default recipes", 15, cursor.getCount());
        cursor.close();
        db.close();
    }

    @Test
    public void testIngredientCRUDOperations() {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();

        // 1. Insert an ingredient
        ContentValues values = new ContentValues();
        values.put("name", "Milk");
        values.put("quantity", 2.0);
        values.put("unit", "litres");
        values.put("expiryDate", "2026-10-15");

        long id = db.insert("ingredients", null, values);
        assertTrue("Inserted row ID should be valid", id > 0);

        // 2. Read the inserted ingredient
        Cursor cursor = db.query("ingredients", null, "id = ?", new String[]{String.valueOf(id)}, null, null, null);
        assertNotNull(cursor);
        assertTrue(cursor.moveToFirst());
        String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        double quantity = cursor.getDouble(cursor.getColumnIndexOrThrow("quantity"));
        String unit = cursor.getString(cursor.getColumnIndexOrThrow("unit"));
        assertEquals("Milk", name);
        assertEquals(2.0, quantity, 0.001);
        assertEquals("litres", unit);
        cursor.close();

        // 3. Update the ingredient
        ContentValues updateValues = new ContentValues();
        updateValues.put("quantity", 3.5);
        int rowsUpdated = db.update("ingredients", updateValues, "id = ?", new String[]{String.valueOf(id)});
        assertEquals(1, rowsUpdated);

        // Verify update
        cursor = db.query("ingredients", null, "id = ?", new String[]{String.valueOf(id)}, null, null, null);
        assertTrue(cursor.moveToFirst());
        assertEquals(3.5, cursor.getDouble(cursor.getColumnIndexOrThrow("quantity")), 0.001);
        cursor.close();

        // 4. Delete the ingredient
        int rowsDeleted = db.delete("ingredients", "id = ?", new String[]{String.valueOf(id)});
        assertEquals(1, rowsDeleted);

        // Verify deletion
        cursor = db.query("ingredients", null, "id = ?", new String[]{String.valueOf(id)}, null, null, null);
        assertFalse(cursor.moveToNext());
        cursor.close();

        db.close();
    }
}
