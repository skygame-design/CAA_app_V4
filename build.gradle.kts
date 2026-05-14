plugins {
    alias(libs.plugins.android.application) apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false // 🟢 On remet la méthode classique "id"
    alias(libs.plugins.kotlin.compose) apply false
}