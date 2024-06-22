import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
	id("com.android.test")
	id("org.jetbrains.kotlin.android")
	id("androidx.baselineprofile")
}

android {
	namespace = "org.nift4.baselineprofile"
	compileSdk = 34

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	kotlinOptions {
		jvmTarget = "17"
	}

	defaultConfig {
		minSdk = 28
		targetSdk = 34

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	targetProjectPath = ":app"

	testOptions.managedDevices.devices {
		create<ManagedVirtualDevice>("pixel6Api34") {
			device = "Pixel 6"
			apiLevel = 34
			systemImageSource = "google"
		}
	}
}


baselineProfile {
	managedDevices += "pixel6Api34"
	useConnectedDevices = false
}

dependencies {
	implementation("androidx.test.ext:junit:1.1.5")
	implementation("androidx.test.espresso:espresso-core:3.5.1")
	implementation("androidx.test.uiautomator:uiautomator:2.3.0")
	implementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
}

androidComponents {
	onVariants { v ->
		val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
		v.instrumentationRunnerArguments.put(
			"targetAppId",
			v.testedApks.map { artifactsLoader.load(it)?.applicationId!! }
		)
	}
}