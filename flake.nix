{
  description = "ModularNuclear - GTNH Minecraft Mod & Reactor Simulator";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      supportedSystems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = nixpkgs.lib.genAttrs supportedSystems;
      pkgsFor = system: import nixpkgs { inherit system; };
    in
    {
      devShells = forAllSystems (system:
        let
          pkgs = pkgsFor system;
        in
        {
          default = pkgs.mkShell {
            name = "modular-nuclear-dev-shell";
            buildInputs = with pkgs; [
              jdk8
              jdk21
              gradle
              git
            ];
          };
        }
      );

      packages = forAllSystems (system:
        let
          pkgs = pkgsFor system;
        in
        {
          default = pkgs.runCommand "modularnuclear-meta" {} ''
            mkdir -p $out
            echo "modularnuclear" > $out/modid
            echo "${self.shortRev or "dirty"}" > $out/rev
          '';
        }
      );
    };
}
