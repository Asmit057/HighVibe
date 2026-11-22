package com.springproject.lovable_clone.intelligence.llm;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Offline generator used when no GEMINI_API_KEY is configured. It follows the same tagged
 * protocol and streams like a real model, so chat, the file-save saga and live previews can be
 * exercised end to end without an API key. It builds a simple landing page from the prompt.
 */
@Component
public class DemoCodeGenerator implements CodeGenerator {

    private static final String[] THEMES = {"emerald", "corporate", "retro", "cyberpunk", "valentine", "garden", "lofi", "dracula"};

    @Override
    public GenerationResult generate(GenerationRequest request, Consumer<String> onDelta) {
        String prompt = request.userMessage().trim();
        String title = titleFrom(prompt);
        String theme = THEMES[Math.floorMod(prompt.hashCode(), THEMES.length)];

        String reply = "<message phase=\"planning\">Demo mode (no GEMINI_API_KEY set): I will turn your idea into a "
                + "landing page in **src/App.tsx** with a reusable **FeatureCard** component.</message>\n"
                + "<file path=\"src/components/FeatureCard.tsx\">" + featureCard() + "</file>\n"
                + "<file path=\"src/App.tsx\">" + app(title, prompt) + "</file>\n"
                + "<file path=\"index.html\">" + indexHtml(title, theme) + "</file>\n"
                + "<message phase=\"completed\">Built **" + title + "** with a hero section, feature cards and a "
                + "call to action using the daisyUI **" + theme + "** theme. Set GEMINI_API_KEY (free at "
                + "aistudio.google.com/apikey) to let the AI build exactly what you describe.</message>";

        // stream in small chunks so the UI behaves exactly like a live model
        for (int i = 0; i < reply.length(); i += 48) {
            onDelta.accept(reply.substring(i, Math.min(reply.length(), i + 48)));
            sleep(8);
        }
        long outputTokens = reply.length() / 4;
        long inputTokens = (prompt.length() + 2000) / 4;
        return new GenerationResult(reply, inputTokens, outputTokens, "demo-generator");
    }

    private static final java.util.Set<String> FILLER = java.util.Set.of(
            "a", "an", "the", "for", "with", "of", "and", "to", "that", "which", "in", "on", "my", "me", "please",
            "build", "create", "make", "design", "generate");

    static String titleFrom(String prompt) {
        String cleaned = prompt.replaceAll("[^\\p{L}\\p{N} ]", " ").trim();
        String words = Arrays.stream(cleaned.split("\\s+"))
                .filter(w -> !w.isBlank())
                .filter(w -> !FILLER.contains(w.toLowerCase(Locale.ROOT)))
                .limit(4)
                .map(w -> w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1))
                .collect(Collectors.joining(" "));
        return words.isBlank() ? "My HighVibe App" : words;
    }

    private static String jsString(String value) {
        StringBuilder out = new StringBuilder("'");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\'' -> out.append("\\'");
                case '\n' -> out.append("\\n");
                case '\r' -> { }
                case '<' -> out.append("\\u003c");
                default -> out.append(c);
            }
        }
        return out.append("'").toString();
    }

    private static String htmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String featureCard() {
        return """

                import type { LucideIcon } from 'lucide-react'

                interface FeatureCardProps {
                  icon: LucideIcon
                  title: string
                  description: string
                }

                export default function FeatureCard({ icon: Icon, title, description }: FeatureCardProps) {
                  return (
                    <article className="card bg-base-100 shadow-md transition hover:-translate-y-1 hover:shadow-xl">
                      <div className="card-body gap-3">
                        <Icon className="h-8 w-8 text-primary" aria-hidden="true" />
                        <h3 className="card-title">{title}</h3>
                        <p className="text-base-content/70">{description}</p>
                      </div>
                    </article>
                  )
                }
                """;
    }

    private static String app(String title, String prompt) {
        return """

                import { Rocket, ShieldCheck, Sparkles, Zap } from 'lucide-react'
                import FeatureCard from './components/FeatureCard'

                const TITLE = %s
                const IDEA = %s

                const features = [
                  { icon: Zap, title: 'Fast by default', description: 'Snappy interactions and instant feedback on every action.' },
                  { icon: ShieldCheck, title: 'Reliable', description: 'Built on a typed, tested React and Vite foundation.' },
                  { icon: Sparkles, title: 'Delightful', description: 'Thoughtful details that make people want to come back.' },
                ]

                export default function App() {
                  return (
                    <div className="min-h-screen bg-base-200">
                      <header className="navbar bg-base-100 shadow-sm px-6">
                        <span className="text-xl font-bold">{TITLE}</span>
                        <div className="ml-auto">
                          <button className="btn btn-primary btn-sm">Get started</button>
                        </div>
                      </header>

                      <main className="mx-auto max-w-5xl px-6 py-16 space-y-16">
                        <section className="text-center space-y-6">
                          <span className="badge badge-secondary badge-lg">New</span>
                          <h1 className="text-5xl font-extrabold tracking-tight">{TITLE}</h1>
                          <p className="mx-auto max-w-2xl text-lg text-base-content/70">{IDEA}</p>
                          <div className="flex justify-center gap-3">
                            <button className="btn btn-primary">
                              <Rocket className="h-4 w-4" aria-hidden="true" /> Launch
                            </button>
                            <button className="btn btn-ghost">Learn more</button>
                          </div>
                        </section>

                        <section className="grid gap-6 md:grid-cols-3">
                          {features.map((feature) => (
                            <FeatureCard key={feature.title} {...feature} />
                          ))}
                        </section>
                      </main>

                      <footer className="footer footer-center p-6 text-base-content/60">
                        <p>Made with HighVibe</p>
                      </footer>
                    </div>
                  )
                }
                """.formatted(jsString(title), jsString(prompt));
    }

    private static String indexHtml(String title, String theme) {
        return """

                <!doctype html>
                <html lang="en" data-theme="%s">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>%s</title>
                  </head>
                  <body>
                    <div id="root"></div>
                    <script type="module" src="/src/main.tsx"></script>
                  </body>
                </html>
                """.formatted(theme, htmlEscape(title));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
