{
  description = "Offline Android speech recognition service powered by NVIDIA Parakeet TDT";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.05";
    flake-utils.url = "github:numtide/flake-utils";
    rust-overlay.url = "github:oxalica/rust-overlay";
  };

  outputs = { self, nixpkgs, flake-utils, rust-overlay }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        overlays = [ (import rust-overlay) ];
        pkgs = import nixpkgs {
          inherit system overlays;
          config.android_sdk.accept_license = true;
          config.allowUnfree = true;
        };

        androidSdkVersion = "34";
        ndkVersion = "26.3.11579264";
        buildToolsVersion = "34.0.0";

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "13.0";
          platformVersions = [ androidSdkVersion ];
          buildToolsVersions = [ buildToolsVersion ];
          includeNDK = true;
          ndkVersions = [ ndkVersion ];
          includeEmulator = false;
          includeSystemImages = false;
        };

        androidSdk = androidComposition.androidsdk;
        androidNdkHome = "${androidSdk}/libexec/android-sdk/ndk/${ndkVersion}";

        rustToolchain = pkgs.rust-bin.stable.latest.default.override {
          targets = [ "aarch64-linux-android" ];
        };

        jdk = pkgs.jdk17;

      in {
        devShells.default = pkgs.mkShell {
          name = "android-parakeet-service";

          buildInputs = with pkgs; [
            jdk
            rustToolchain
            pkgs.cargo-ndk
            androidSdk
            gradle
            git
            curl
            cacert
            pkg-config
          ];

          ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
          ANDROID_NDK_HOME = androidNdkHome;
          NDK_HOME = androidNdkHome;

          JAVA_HOME = jdk.home;

          GRADLE_OPTS = "-Dorg.gradle.daemon=false -Xmx4g";
          ANDROID_USE_ANDROIDX = "true";

          shellHook = ''
            echo "Android Parakeet Service dev shell"
            echo "  JDK:        $(javac -version 2>&1)"
            echo "  Rust:       $(rustc --version)"
            echo "  cargo-ndk:  $(cargo ndk --version 2>&1 || echo 'not found')"
            echo "  ANDROID_HOME=$ANDROID_HOME"
            echo "  NDK_HOME=$NDK_HOME"
          '';
        };
      });
}
