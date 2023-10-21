package io.papermc.restamp.cli;

import picocli.CommandLine;

public class RestampCLIVersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[]{this.getClass().getPackage().getSpecificationVersion()};
    }

}
