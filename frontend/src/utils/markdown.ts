import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({
  breaks: true,
  gfm: true,
})

export function renderMarkdown(content: string): string {
  const raw = marked.parse(content) as string
  return DOMPurify.sanitize(raw)
}