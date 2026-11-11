package uk.woozy.fit.data.db

import uk.woozy.fit.data.off.FoodDraft
import uk.woozy.fit.model.Nutrients

/** Saving foods that came from outside. Everything fetched is kept, so it works offline next time. */
object Foods {
    /** Returns the stored food for this draft, saving it first if its barcode is new. Drafts with no barcode are always saved. */
    suspend fun saveDraft(db: HqFitDatabase, draft: FoodDraft, nowMs: Long): FoodEntity {
        draft.barcode?.let { code -> db.foods().byBarcode(code)?.let { return it } }
        val row = FoodEntity(
            name = draft.name, brand = draft.brand, barcode = draft.barcode, source = "off",
            servingG = draft.servingG, servingLabel = draft.servingLabel, per100 = Nutrients.encode(draft.per100),
            incomplete = draft.incomplete, createdMs = nowMs,
        )
        return row.copy(id = db.foods().insert(row))
    }
}
