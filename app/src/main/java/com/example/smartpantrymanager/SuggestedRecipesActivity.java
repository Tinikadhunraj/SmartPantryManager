package com.example.smartpantrymanager;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SuggestedRecipesActivity extends AppCompatActivity {

    private RecyclerView recipesListView;
    private TextView noRecipesNotice;
    private TextView tvSubtitle;
    private Button btnBackToPantry;
    private Button btnFilterSuggested;
    private Button btnFilterAll;

    private DatabaseHelper dbHelper;
    private RecipeAdapter adapter;
    private final List<Recipe> availableRecipes = new ArrayList<>();
    private boolean showAllRecipes = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_suggested_recipes);

        recipesListView = findViewById(R.id.recyclerViewRecipes);
        noRecipesNotice = findViewById(R.id.tvNoRecipes);
        tvSubtitle = findViewById(R.id.tvSuggestedRecipesSubtitle);

        btnBackToPantry = findViewById(R.id.btnBackFromSuggestedRecipes);
        btnFilterSuggested = findViewById(R.id.btnFilterSuggested);
        btnFilterAll = findViewById(R.id.btnFilterAll);

        btnBackToPantry.setOnClickListener(v -> finish());

        btnFilterSuggested.setOnClickListener(v -> {
            showAllRecipes = false;
            updateFilterButtons();
            updateSuggestedRecipes();
        });

        btnFilterAll.setOnClickListener(v -> {
            showAllRecipes = true;
            updateFilterButtons();
            updateSuggestedRecipes();
        });

        dbHelper = new DatabaseHelper(this);

        setupRecyclerView();
        updateFilterButtons();
        updateSuggestedRecipes();
    }

    private void updateFilterButtons() {
        if (showAllRecipes) {
            btnFilterAll.setAlpha(1.0f);
            btnFilterSuggested.setAlpha(0.6f);
            tvSubtitle.setText("Browsing all available recipes.");
        } else {
            btnFilterSuggested.setAlpha(1.0f);
            btnFilterAll.setAlpha(0.6f);
            tvSubtitle.setText("Recipes you can make with your current pantry.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dbHelper != null) {
            updateSuggestedRecipes();
        }
    }

    private void setupRecyclerView() {
        recipesListView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecipeAdapter(availableRecipes);
        recipesListView.setAdapter(adapter);
    }

    private void updateSuggestedRecipes() {
        availableRecipes.clear();

        List<Ingredient> pantryContents = getPantryIngredients();
        List<Recipe> allRecipes = getAllRecipesFromDatabase();

        if (showAllRecipes) {
            availableRecipes.addAll(allRecipes);
        } else {
            for (Recipe r : allRecipes) {
                if (isRecipeFeasible(r, pantryContents)) {
                    availableRecipes.add(r);
                }
            }
        }

        adapter.notifyDataSetChanged();
        toggleRecipeVisibility(availableRecipes.isEmpty());
    }

    private void toggleRecipeVisibility(boolean noRecipesAvailable) {
        if (noRecipesAvailable) {
            noRecipesNotice.setVisibility(View.VISIBLE);
            recipesListView.setVisibility(View.GONE);
        } else {
            noRecipesNotice.setVisibility(View.GONE);
            recipesListView.setVisibility(View.VISIBLE);
        }
    }

    private List<Ingredient> getPantryIngredients() {
        List<Ingredient> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        try (Cursor cursor = db.query(
                "ingredients", null, null, null, null, null,
                "name ASC")) {

            while (cursor.moveToNext()) {
                list.add(new Ingredient(
                        cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getDouble(cursor.getColumnIndexOrThrow("quantity")),
                        cursor.getString(cursor.getColumnIndexOrThrow("unit")),
                        cursor.getString(cursor.getColumnIndexOrThrow("expiryDate"))
                ));
            }
        }

        db.close();
        return list;
    }

    private List<Recipe> getAllRecipesFromDatabase() {
        List<Recipe> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        try (Cursor cursor = db.query(
                "recipes", null, null, null, null, null,
                "name ASC")) {

            while (cursor.moveToNext()) {
                list.add(new Recipe(
                        cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("description")),
                        cursor.getString(cursor.getColumnIndexOrThrow("ingredients")),
                        cursor.getString(cursor.getColumnIndexOrThrow("instructions"))
                ));
            }
        }

        db.close();
        return list;
    }

    private boolean isRecipeFeasible(Recipe recipe, List<Ingredient> pantry) {
        if (pantry == null || pantry.isEmpty()) return false;

        String[] neededIngredients = recipe.getIngredients().split(",");
        int matchedCount = 0;

        for (String ingredient : neededIngredients) {
            String[] details = ingredient.split(":", 3);
            if (details.length != 3) continue;

            String requiredName = details[0];
            double requiredQty = 0;
            try {
                requiredQty = Double.parseDouble(details[1]);
            } catch (NumberFormatException ignored) {}

            String requiredUnit = standardizeUnit(details[2]);
            double requiredBaseQty = toBaseUnitQuantity(requiredQty, requiredUnit);

            double pantrySum = 0;
            boolean foundNameMatch = false;

            for (Ingredient available : pantry) {
                if (isNameMatching(available.getName(), requiredName)) {
                    foundNameMatch = true;
                    if (areUnitsCompatible(standardizeUnit(available.getUnit()), requiredUnit)) {
                        pantrySum += toBaseUnitQuantity(available.getQuantity(), standardizeUnit(available.getUnit()));
                    } else {
                        pantrySum += available.getQuantity();
                    }
                }
            }

            if (pantrySum >= requiredBaseQty || (foundNameMatch && pantrySum > 0)) {
                matchedCount++;
            }
        }

        return matchedCount == neededIngredients.length || (neededIngredients.length > 2 && matchedCount >= neededIngredients.length - 1);
    }

    private boolean isNameMatching(String pantryName, String recipeIngredientName) {
        String p = standardizeName(pantryName);
        String r = standardizeName(recipeIngredientName);

        if (p.equals(r)) return true;
        if (p.contains(r) || r.contains(p)) return true;

        return false;
    }

    private String standardizeName(String name) {
        if (name == null) return "";
        String low = name.toLowerCase(Locale.ROOT).trim();

        if (low.equals("tomatoes") || low.equals("tomatoe")) return "tomato";
        if (low.equals("potatoes") || low.equals("potatoe")) return "potato";
        if (low.equals("onions")) return "onion";
        if (low.equals("eggs")) return "egg";
        if (low.equals("bananas")) return "banana";
        if (low.equals("carrots")) return "carrot";
        if (low.equals("cheeses")) return "cheese";
        if (low.endsWith("ies")) return low.substring(0, low.length() - 3) + "y";
        if (low.endsWith("s") && !low.endsWith("ss") && !low.endsWith("us") && !low.endsWith("is")) {
            return low.substring(0, low.length() - 1);
        }

        return low;
    }

    private String standardizeUnit(String unit) {
        if (unit == null) return "items";
        String u = unit.toLowerCase(Locale.ROOT).trim();
        switch (u) {
            case "item":
            case "items":
            case "piece":
            case "pieces":
            case "pc":
            case "pcs":
            case "whole":
            case "unit":
            case "units":
            case "slice":
            case "slices":
            case "block":
            case "blocks":
            case "":
                return "items";
            case "g":
            case "gram":
            case "grams":
                return "grams";
            case "kg":
            case "kilogram":
            case "kilograms":
                return "kg";
            case "ml":
            case "millilitre":
            case "millilitres":
                return "ml";
            case "l":
            case "litre":
            case "litres":
                return "l";
            default:
                return u;
        }
    }

    private boolean areUnitsCompatible(String unitA, String unitB) {
        if (unitA.equals(unitB)) return true;

        boolean isWeightA = unitA.equals("grams") || unitA.equals("kg");
        boolean isWeightB = unitB.equals("grams") || unitB.equals("kg");
        if (isWeightA && isWeightB) return true;

        boolean isVolumeA = unitA.equals("ml") || unitA.equals("l");
        boolean isVolumeB = unitB.equals("ml") || unitB.equals("l");
        if (isVolumeA && isVolumeB) return true;

        return true;
    }

    private double toBaseUnitQuantity(double quantity, String unit) {
        switch (unit) {
            case "kg": return quantity * 1000;
            case "grams": return quantity;
            case "l": return quantity * 1000;
            case "ml": return quantity;
            case "items": return quantity;
            default: return quantity;
        }
    }
}
