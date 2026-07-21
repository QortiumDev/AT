# Automated Transactions

This repository contains the Automated Transactions virtual machine maintained
for Qortium.

`QortiumDev/AT` is an independent GitHub repository that preserves the complete
development history inherited through `ciyam/AT`, `catbref/AT`, and
`IceBurst/AT`. See [AUTHORS.md](AUTHORS.md) for project lineage and attribution.

## Build and test

The Java implementation requires JDK 11 or newer.

```sh
cd Java
mvn clean verify -DskipTests=false
```

## License

This project is distributed under the [MIT License](LICENSE).
