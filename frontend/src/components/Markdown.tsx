import { Fragment } from 'react'
import { tokenizeMarkdown } from '../lib/parse'

export default function Markdown({ text }: { text: string }) {
  return (
    <>
      {tokenizeMarkdown(text).map((token, i) => {
        switch (token.kind) {
          case 'bold':
            return <strong key={i} className="font-semibold text-white">{token.value}</strong>
          case 'code':
            return <code key={i} className="rounded bg-white/10 px-1 py-0.5 font-mono text-[0.85em]">{token.value}</code>
          case 'br':
            return <br key={i} />
          default:
            return <Fragment key={i}>{token.value}</Fragment>
        }
      })}
    </>
  )
}
