package ru.mipt.npm.reactor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D
import org.apache.commons.math3.random.JDKRandomGenerator
import org.apache.commons.math3.random.RandomGenerator
import org.apache.commons.math3.random.SynchronizedRandomGenerator
import ru.mipt.npm.reactor.model.Generation
import ru.mipt.npm.reactor.model.Particle
import ru.mipt.npm.reactor.model.Photon
import ru.mipt.npm.reactor.model.SimpleAtmosphere
import java.io.BufferedWriter
import java.nio.file.Paths

fun createOptins(): Options {
    val options = Options()

    options.addOption(
        Option
            .builder("h")
            .longOpt("help")
            .desc("print this message and exit")
            .required(false)
            .build()

    )
    options.addOption(
        Option
            .builder("v")
            .longOpt("version")
            .desc("print information about version and exit")
            .required(false)
            .build()
    )

    options.addOption(
        Option
            .builder("o")
            .longOpt("output")
            .hasArg(true)
            .desc("print simulation result in the file with given name")
            .required(false)
            .argName("FILENAME")
            .build()

    )

    options.addOption(
        Option
            .builder("g")
            .longOpt("gain")
            .hasArg(true)
            .desc("set the local coefficient of gamma multiplication")
            .required(false)
            .argName("NUMBER")
            .build()

    )

    options.addOption(
        Option
            .builder()
            .longOpt("free-path")
            .hasArg(true)
            .desc("set the photon free mean path")
            .required(false)
            .argName("NUMBER")
            .build()

    )
    options.addOption(
        Option
            .builder()
            .longOpt("cell-length")
            .hasArg(true)
            .desc("set the length of acceleration cell (influence on birth point of new photon)")
            .required(false)
            .argName("NUMBER")
            .build()

    )
    options.addOption(
        Option
            .builder()
            .longOpt("cloud-size")
            .hasArg(true)
            .desc("set the cloud size")
            .required(false)
            .argName("NUMBER")
            .build()

    )
    options.addOption(
        Option
            .builder()
            .longOpt("field-magnitude")
            .hasArg(true)
            .desc("set the field magnitude")
            .required(false)
            .argName("NUMBER")
            .build()

    )
    options.addOption(
        Option
            .builder("l")
            .longOpt("particle-limit")
            .hasArg(true)
            .desc("set the upper limit of number of particle")
            .required(false)
            .argName("NUMBER")
            .build()


    )
    options.addOption(
        Option
            .builder("s")
            .longOpt("seed")
            .hasArg(true)
            .desc("set the random generator seed")
            .required(false)
            .argName("NUMBER")
            .build()


    )
    options.addOption(
        Option
            .builder()
            .longOpt("seed-photons")
            .hasArg(true)
            .desc(("""
                set the path to file contains list of seed photons in next format:
                POSITION_X POSITION_Y POSITION_Z DIRECTION_X DIRECTION_Y DIRECTION_Z ENERGY NUMBER
                POSITION_X POSITION_Y POSITION_Z DIRECTION_X DIRECTION_Y DIRECTION_Z ENERGY NUMBER
                ...
                by default using:
                0.0 0.0 cloud-size/2 0.0 0.0 -1.0 1.0 1
            """.trimIndent()))
            .required(false)
            .argName("FILENAME")
            .build()


    )

    options.addOption(
        Option
            .builder()
            .longOpt("save-plot")
            .hasArg(true)
            .desc("save graph of simulation result in the html-file with given name")
            .required(false)
            .argName("FILENAME")
            .build()

    )

    options.addOption(
        Option
            .builder()
            .longOpt("dynamic-plot")
            .desc("start server with dynamic plot")
            .required(false)
            .build()

    )

    return options
}

fun parseError(exp: Exception, message: String = ""): Nothing {
    println(exp)
    print(message)
    println("Use \"$programmName --help\" for additional information")
    throw exp
}

fun getAtmosphere(cmd: CommandLine, rng: RandomGenerator): SimpleAtmosphere {
    val init = object {
        var multiplication: Double = 2.0
        var photonFreePath: Double = 100.0
        var cellLength: Double = 100.0
        var cloudSize: Double = 1000.0
        var fieldMagnitude: Double = 0.2
    }

    try {
        when {
            cmd.hasOption("gain") -> init.multiplication = cmd.getOptionValue("gain").toDouble()
            cmd.hasOption("free-path") -> init.photonFreePath = cmd.getOptionValue("gain").toDouble()
            cmd.hasOption("cell-length") -> init.cellLength = cmd.getOptionValue("gain").toDouble()
            cmd.hasOption("cloud-size") -> init.cloudSize = cmd.getOptionValue("gain").toDouble()
            cmd.hasOption("field-magnitude") -> init.fieldMagnitude = cmd.getOptionValue("gain").toDouble()
        }
    } catch (exp: Exception) {
        parseError(exp)
    }
    return SimpleAtmosphere(
        init.multiplication,
        init.photonFreePath,
        init.cellLength,
        init.cloudSize,
        init.fieldMagnitude,
        rng
    )
}

fun getLimitOfPhotons(cmd: CommandLine): Int {
    if (cmd.hasOption("particle-limit")) {
        try {
            return cmd.getOptionValue("particle-limit").toInt()
        } catch (exp: Exception) {
            parseError(exp, "Options particle-limit can't be equal ${cmd.getOptionValue("particle-limit")}\n")
        }
    }
    return 10000
}

fun getRandomGenerator(cmd: CommandLine): RandomGenerator {
    if (cmd.hasOption("seed")) {
        try {
            val seed = cmd.getOptionValue("particle-limit").toInt()
            return SynchronizedRandomGenerator(JDKRandomGenerator(seed))
        } catch (exp: Exception) {
            parseError(exp, "Options particle-limit can't be equal ${cmd.getOptionValue("particle-limit")}\n")
        }
    }
    return SynchronizedRandomGenerator(JDKRandomGenerator())
}

fun getSeed(cmd: CommandLine, atmosphere: SimpleAtmosphere): List<Particle> {
    return if (cmd.hasOption("seed-photons")) {
        try {
            val path = Paths.get(cmd.getOptionValue("seed-photons"))
            val text = path.toFile().readText().split("\n")
            text.map {
                val temp = it.split(" ")
                if (temp.size < 7) {
                    error("Bad format of input file with seed photons")
                }
                val position = Vector3D(temp[0].toDouble(), temp[1].toDouble(), temp[2].toDouble())
                val direction = Vector3D(temp[3].toDouble(), temp[4].toDouble(), temp[5].toDouble())
                val energy = temp[6].toDouble()
                val number: Int = if (temp.size == 7) {
                    1
                } else {
                    temp[7].toInt()
                }
                List(number) {
                    Photon(position, direction, energy)
                }
            }.reduce { acc, list -> acc + list }
        } catch (exp: Exception) {
            parseError(exp,
                "Options seed-photons can't be equal ${cmd.getOptionValue("seed-photons")} or input file have bad format\n")
        }
    } else {
        listOf(Photon(Vector3D(0.0, 0.0, atmosphere.cloudSize / 2), Vector3D(0.0, 0.0, -1.0), 1.0))
    }
}

fun Flow<Generation>.withLogging(cmd: CommandLine): Flow<Generation> {
    val writer: BufferedWriter
    val template: (Int, Int, Double) -> String
    if (cmd.hasOption("o")) {
        val path = Paths.get(cmd.getOptionValue("o")).toFile()
        writer = path.bufferedWriter()
        writer.write("%10s %7s %7s".format("generation", "number", "height\n"))
        template = { i: Int, i1: Int, d: Double -> "%10d %7d %7.2f\n".format(i, i1, d) }
    } else {
        writer = System.out.bufferedWriter()
        template = { i: Int, i1: Int, d: Double -> "There are $i1 photons in generation $i . Average height is $d\n" }
    }
    return onEach { generation ->
        val height = generation.particles.map { it.origin.z }.average()
        withContext(Dispatchers.IO) {
            writer.write(template(generation.index, generation.particles.size, height))
            writer.newLine()
            writer.flush()
        }
    }
}


