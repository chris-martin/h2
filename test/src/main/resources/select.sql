select
  document.doc_id,
  document.title,
  document.released,
  document.author_id,
  author.person_name author_name,
  page.page_number page,
  document.marking doc_marking,
  page.marking page_marking,
  page.page_text
from vault.document
left join vault.page
on document.doc_id = page.doc_id
left join public.person author
on document.author_id = author.person_id
limit 20;
