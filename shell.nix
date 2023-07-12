{ pkgs ? import (fetchTarball
  "https://github.com/NixOS/nixpkgs/archive/65a2ffba207a43c13ab8eff94986199bb90048f4.tar.gz")
  { } }:

pkgs.mkShell {
  buildInputs = [
    pkgs.jre17_minimal
    pkgs.maven
  ];
}
