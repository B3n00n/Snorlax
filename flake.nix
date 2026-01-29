{
  description = "Snorlax setup tool development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
        };

        # Python with required packages
        pythonEnv = pkgs.python311.withPackages (ps: with ps; [
          customtkinter
          pillow
          pyinstaller
        ]);

        # Windows build tools
        windowsBuildInputs = with pkgs; [
          # Wine for testing Windows executables
          wine64

          # Windows cross-compilation support
          pkgsCross.mingwW64.stdenv.cc
          pkgsCross.mingwW64.windows.pthreads
        ];

      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            # Python environment
            pythonEnv

            # Android debugging tools (for ADB)
            android-tools

            # Build tools
            git
          ] ++ windowsBuildInputs;

          shellHook = ''
            echo "Launched Snorlax Setup Dev Environment"
            echo "Python version: ${pythonEnv.interpreter.version}"
            echo ""
            echo "Available commands:"
            echo "  python main.py              - Run the GUI setup tool"
            echo "  python main.py --cli        - Run in CLI mode"
            echo "  pyinstaller setup.spec      - Build Windows executable"
            echo ""
            echo "To build for Windows:"
            echo "  cd setup"
            echo "  python -m PyInstaller --onefile --windowed --icon=resources/icon.ico --name=SnorlaxSetup --add-data \"config.py;.\" --add-data \"src;src\" --hidden-import=customtkinter main.py"
          '';
        };
      }
    );
}
