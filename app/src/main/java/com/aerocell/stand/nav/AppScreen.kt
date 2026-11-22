package com.aerocell.stand.nav

/** Экран стенда: главное меню или сравнение коробов. Калькулятор и каталог — окно поверх меню. */
enum class AppScreen { MENU, COMPARISON }

/** Правила переходов без Android, покрыты тестами. */
object ScreenRules {
    /** С чего стартует приложение. */
    val START: AppScreen = AppScreen.MENU

    /** После простоя посетитель всегда начинает с меню. */
    fun afterIdle(): AppScreen = AppScreen.MENU

    /** Музыка играет только на экране сравнения. */
    fun musicAllowed(screen: AppScreen): Boolean = screen == AppScreen.COMPARISON

    /** Подсказку с пальцем показываем только на сравнении, без простоя и без окна с сайтом. */
    fun hintAllowed(screen: AppScreen, idle: Boolean, webOpen: Boolean): Boolean =
        screen == AppScreen.COMPARISON && !idle && !webOpen

    /** Окно с сайтом открывается только из меню. */
    fun webAllowed(screen: AppScreen): Boolean = screen == AppScreen.MENU
}
