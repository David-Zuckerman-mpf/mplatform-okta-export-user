insert import_status(last_import)
select '1900-01-01' FROM DUAL
where not exists(select 1 from import_status where last_import = '1900-01-01')