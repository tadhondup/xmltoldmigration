# Migrate from TBRC XML files to BDRC RDF LD

This repository contains code that will be used to migrate from TBRC XML files to the new Linked Data.

## Compiling and running

Simple run:

```
mvn compile exec:java -Dexec.args=-useCouchdb
```

Compiling and generating jar file:

```
mvn clean package
```

If the above fails try:

```
git submodule update --init
```

Running the jar file:

```
java -jar target/xmltoldmigration-0.2.0.jar [-useCouchdb] [-datadir <dir path>] [-outdir <dir path>]
```

All args are optional. By default the jsonld files are written to `./tbrc-jsonld/`. If the `-outdir` is present it must end with `'/'`. Similarly, by default the data is read from `./tbrc/`, and if the `-datadir` is present it must be terminated with `'/'`. Finally, by default the files are not loaded into couchdb so if one wants to upload to couchdb then `-useCouhdb` needs to be supplied.

## TODO

- map all nodes of O5TAX003 to topics, to map place/event/affiliation/@rid="lineage:*"
- add licenses as individuals?
- handle BDRC staff by some individuals of a new class?
- check if the "onDisk" description type appear outside imagegroup
- migrate some GIS id (WB_area_sq_km, etc.) to descriptions
- migrate most description to more specific properties (first batch done, more to be done later)
- some properties have a useless lang tag: gbdist, gonpaPer1000, etc.
- https://www.tbrc.org/xmldoc?rid=O5TAX003 has two "TaklungKagyu" entries, does it matter?
- Outline nodes labels are based on names, but when absent, it may need to be based on titles?
- make sure the vol info is present in outline locations when a work has more than 1 volume
- link the volumes to the work?
- add name in the volumes? (extracted from outlines)
- differentiate migration exception types? or export migration exceptions to csv files?
- check that children cannot contain cycles, Children must be born after the parent, deathDate must be after birthDate ([source](https://www.w3.org/TR/shacl-ucr/#dfn-uc23))
- pubinfo properties' lang/encoding tags have a difficult history and should be fixed. Some euristics should be found to apply them correctly
- ignore fields commented in https://github.com/BuddhistDigitalResourceCenter/xmltoldmigration/commit/068af86db28e70b7d7960ce989a0fab35b03f66a (handling it differently in the xsd file so that the xml files still validate)


## License

The code is Copyright 2017 Buddhist Digital Resource Center, and is provided under [Apache License 2.0](LICENSE).
