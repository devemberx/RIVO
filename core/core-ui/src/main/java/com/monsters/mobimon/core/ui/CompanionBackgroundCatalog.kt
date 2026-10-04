package com.monsters.mobimon.core.ui

val CompanionBackgroundCatalog =
    BackgroundCatalog(
        scenes =
            listOf(
                BackgroundScene(
                    id = "lake_park",
                    nameRes = R.string.pet_scene_lake_park_name,
                    descriptionRes = R.string.pet_scene_lake_park_description,
                    artwork =
                        BackgroundArtwork.ByPeriod(
                            BackgroundFrames(
                                midnight = BackgroundFrame(R.drawable.pet_background_lake_park_midnight),
                                sunrise = BackgroundFrame(R.drawable.pet_background_lake_park_sunrise, 0.08f, true),
                                morning = BackgroundFrame(R.drawable.pet_background_lake_park_morning, 0.08f, true),
                                day = BackgroundFrame(R.drawable.pet_background_lake_park_day, 0.12f, true),
                                afternoon = BackgroundFrame(R.drawable.pet_background_lake_park_afternoon, 0.10f, true),
                                sunset = BackgroundFrame(R.drawable.pet_background_lake_park_sunset, 0.06f, true),
                                night = BackgroundFrame(R.drawable.pet_background_lake_park_night),
                            ),
                        ),
                ),
                BackgroundScene(
                    id = "cyberpunk_city",
                    nameRes = R.string.pet_scene_cyberpunk_city_name,
                    descriptionRes = R.string.pet_scene_cyberpunk_city_description,
                    artwork =
                        BackgroundArtwork.ByPeriod(
                            BackgroundFrames(
                                midnight = BackgroundFrame(R.drawable.pet_background_cyberpunk_city_midnight),
                                sunrise =
                                    BackgroundFrame(
                                        R.drawable.pet_background_cyberpunk_city_sunrise,
                                        0.08f,
                                        true,
                                    ),
                                morning =
                                    BackgroundFrame(
                                        R.drawable.pet_background_cyberpunk_city_morning,
                                        0.08f,
                                        true,
                                    ),
                                day =
                                    BackgroundFrame(
                                        R.drawable.pet_background_cyberpunk_city_day,
                                        0.12f,
                                        true,
                                    ),
                                afternoon =
                                    BackgroundFrame(
                                        R.drawable.pet_background_cyberpunk_city_afternoon,
                                        0.10f,
                                        true,
                                    ),
                                sunset =
                                    BackgroundFrame(
                                        R.drawable.pet_background_cyberpunk_city_sunset,
                                        0.02f,
                                        true,
                                    ),
                                night = BackgroundFrame(R.drawable.pet_background_cyberpunk_city_night),
                            ),
                        ),
                ),
            ),
        items =
            listOf(
                BackgroundItem("none:background", BackgroundVisual.Scene("lake_park")),
                BackgroundItem("background:default", BackgroundVisual.Scene("lake_park")),
                BackgroundItem("background:lake_park", BackgroundVisual.Scene("lake_park")),
                BackgroundItem("background:cyberpunk_city", BackgroundVisual.Scene("cyberpunk_city")),
                BackgroundItem("background:star_hanger", BackgroundVisual.Prop(BackgroundProp.STAR_HANGER)),
                BackgroundItem(
                    "background:starlight_yarn_basket",
                    BackgroundVisual.Prop(BackgroundProp.STARLIGHT_YARN_BASKET),
                ),
                BackgroundItem("background:star", BackgroundVisual.Effect(ParticleType.STAR)),
                BackgroundItem("background:snow", BackgroundVisual.Effect(ParticleType.SNOW)),
                BackgroundItem("background:petal", BackgroundVisual.Effect(ParticleType.PETAL)),
            ),
        defaultSceneId = "lake_park",
    )
