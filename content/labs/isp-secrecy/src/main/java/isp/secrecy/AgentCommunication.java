package isp.secrecy;

import fri.isp.Agent;
import fri.isp.Environment;
import java.nio.charset.StandardCharsets;

/**
 * A communication channel is implemented with a thread-safe blocking queue.
 * <p/>
 * Both agents are implemented as anonymous subclasses of Agent,
 * overriding task().
 * <p/>
 * Both agents are started at the end of the main method definition below.
 */
public class AgentCommunication {
    public static void main(String[] args) {
        final Environment env = new Environment();

        // Register Alice as a participant and define that agent's task.
        env.add(new Agent("alice") {
            // Override the Agent task method so the environment runs the steps below.
            @Override
            // Run Alice's communication steps.
            public void task() {
                // Encode Alice's greeting as UTF-8 because the communication channel transports byte arrays.
                final byte[] payload = "Hi, Bob, this is Alice.".getBytes(StandardCharsets.UTF_8);
                // Send Bob Alice's UTF-8 greeting bytes without encryption.
                send("bob", payload);
                // Block until Bob's next byte-array message is available.
                final byte[] received = receive("bob");
                // Display Bob's bytes in hexadecimal and then decode the same bytes as UTF-8 text.
                print("Got '%s', converted to string: '%s'", hex(received), new String(received, StandardCharsets.UTF_8));
            // Finish Alice's task after its communication steps complete.
            }
        // Finish defining Alice and add this agent to the environment.
        });

        // Register Bob as a participant and define that agent's task.
        env.add(new Agent("bob") {
            // Override the Agent task method so the environment runs the steps below.
            @Override
            // Run Bob's communication steps.
            public void task() {
                // Encode Bob's greeting as UTF-8 and send its bytes to Alice without encryption.
                send("alice", "Hey Alice, Bob here.".getBytes(StandardCharsets.UTF_8));
                // Wait for Alice's greeting, decode it as UTF-8, and display it.
                print("Got '%s'", new String(receive("alice"), StandardCharsets.UTF_8));
            // Finish Bob's task after its communication steps complete.
            }
        // Finish defining Bob and add this agent to the environment.
        });

        env.connect("alice", "bob");
        env.start();
    }
}
